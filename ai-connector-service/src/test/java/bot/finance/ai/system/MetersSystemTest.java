package bot.finance.ai.system;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.metrics.MeterName;
import bot.finance.ai.common.boot.AbstractMemorySystemTest;
import bot.finance.ai.common.fixtures.CallerTokens;
import bot.finance.ai.common.fixtures.ChatCompletionFixtures;
import bot.finance.ai.common.fixtures.EmbeddingFixtures;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.stubs.AuthorizedStubs;
import bot.finance.ai.common.stubs.McpLedgerStubs;
import bot.finance.ai.common.stubs.WireMockStubs;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.restassured.response.Response;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers {@code GET /actuator/prometheus} against the fully wired application, with the memory on so the recall,
 * change-stream and pending-entries beans are all present ({@link AbstractMemorySystemTest} against the real,
 * containerized database and Redis). The provider timers, token counters and tool timer are the framework's own
 * observations ({@code ChatObservationAutoConfiguration}); nothing here records them by hand.
 */
class MetersSystemTest extends AbstractMemorySystemTest {

    private static final String GROUPING = RequestFixtures.DEFAULT_CATEGORY_GROUPINGS.getFirst();

    private static String proposalArguments() {
        return """
                {"category":"Lunch","grouping":"%s","description":"lunch","amount":"15.00",\
                "currencyCode":"EUR","merchant":"Deli Co"}"""
                .formatted(GROUPING);
    }

    private static double gaugeValue(String body, String metricName) {
        Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(metricName) + "\\s+(\\S+)$")
                .matcher(body);
        assertThat(matcher.find()).as(metricName + " sample present").isTrue();
        return Double.parseDouble(matcher.group(1));
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a turn runs and prometheus is scraped - then it carries the rpc, chat, tool, embedding "
                + "and pending samples")
        void whenTurnRunsAndPrometheusIsScraped_thenCarriesRpcChatToolEmbeddingAndPendingSamples() {
            long userId = 9001L;
            String incomingMessageId = "message-9001-meters";
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubEmbeddings(EmbeddingFixtures.embeddingsResponse(EmbeddingFixtures.unitVector(0)));
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-1", proposalArguments())),
                    ChatCompletionFixtures.textResponse("recorded"));

            String token = CallerTokens.bearer(userId, incomingMessageId);
            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, token)
                    .extractIntents(RequestFixtures.request());
            log.info("response: {}", response);
            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());

            Response scrape = given().port(actuatorPort).when().get("/actuator/prometheus");
            scrape.then().statusCode(200);
            String body = scrape.getBody().asString();
            log.info("scrape: {}", body);

            assertThat(body)
                    .as("grpc server sample for the ExtractIntents RPC")
                    .containsPattern(Pattern.compile("(?m)^grpc_server_seconds_count\\{(?=[^}]*ExtractIntents)[^}]*}"));
            assertThat(body)
                    .as("chat-tagged gen_ai client operation sample")
                    .containsPattern(
                            Pattern.compile(
                                    "(?m)^gen_ai_client_operation_seconds_count\\{(?=[^}]*gen_ai_operation_name=\"chat\")[^}]*}"));
            assertThat(body)
                    .as("grown input token counter")
                    .containsPattern(
                            Pattern.compile(
                                    "(?m)^gen_ai_client_token_usage_total\\{(?=[^}]*gen_ai_token_type=\"input\")[^}]*}\\s+[1-9]"));
            assertThat(body)
                    .as("grown output token counter")
                    .containsPattern(
                            Pattern.compile(
                                    "(?m)^gen_ai_client_token_usage_total\\{(?=[^}]*gen_ai_token_type=\"output\")[^}]*}\\s+[1-9]"));
            assertThat(body)
                    .as("tool sample whose tool tag ends in the tool's name")
                    .containsPattern(Pattern.compile(
                            "(?m)^spring_ai_tool_seconds_count\\{(?=[^}]*spring_ai_tool_definition_name=\"[^\"]*"
                                    + "create_expense_proposal\")[^}]*}"));
            assertThat(body)
                    .as("embedding-tagged gen_ai client operation sample")
                    .containsPattern(Pattern.compile(
                            "(?m)^gen_ai_client_operation_seconds_count\\{(?=[^}]*gen_ai_operation_name=\"embedding"
                                    + "\")[^}]*}"));

            assertThat(gaugeValue(body, MeterName.CDC_ENTRIES_PENDING.meterName()))
                    .as("the pending gauge reads a numeric 0, not NaN")
                    .isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the provider refuses the chat call - then the rpc fails and the chat sample carries "
                + "the error tag")
        void whenProviderRefusesChatCall_thenRpcFailsAndChatSampleCarriesErrorTag() {
            long userId = 9002L;
            String incomingMessageId = "message-9002-meters";
            WireMockStubs.stubChatCompletionServerError();

            String token = CallerTokens.bearer(userId, incomingMessageId);
            assertThatThrownBy(() -> AuthorizedStubs.withCallerToken(intentExtractionStub, token)
                            .extractIntents(RequestFixtures.request()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAVAILABLE);

            Response scrape = given().port(actuatorPort).when().get("/actuator/prometheus");
            scrape.then().statusCode(200);
            String body = scrape.getBody().asString();
            log.info("scrape: {}", body);

            assertThat(body)
                    .as("the chat operation's sample carries a non-none error tag")
                    .containsPattern(Pattern.compile("(?m)^gen_ai_client_operation_seconds_count\\{"
                            + "(?=[^}]*gen_ai_operation_name=\"chat\")(?=[^}]*error=\"(?!none\")[^\"]*\")[^}]*}"));
        }
    }
}
