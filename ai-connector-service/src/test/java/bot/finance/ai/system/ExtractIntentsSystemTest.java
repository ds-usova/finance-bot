package bot.finance.ai.system;

import static bot.finance.ai.common.PrometheusScrapeUtils.sampleOrZero;
import static bot.finance.ai.common.PrometheusScrapeUtils.scrape;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.metrics.MeterName;
import bot.finance.ai.common.boot.AbstractSystemTest;
import bot.finance.ai.common.containers.LedgerTurnServer.RecordedCall;
import bot.finance.ai.common.containers.WireMockSupport;
import bot.finance.ai.common.fixtures.CallerTokens;
import bot.finance.ai.common.fixtures.ChatCompletionFixtures;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.stubs.AuthorizedStubs;
import bot.finance.ai.common.stubs.CapturedRequestUtils;
import bot.finance.ai.common.stubs.ClassifierStubs;
import bot.finance.ai.common.stubs.LedgerTurnStubs;
import bot.finance.ai.common.stubs.McpLedgerStubs;
import bot.finance.ai.common.stubs.WireMockStubs;
import bot.finance.ledger.adapter.grpc.v1.EndTurnRequest;
import bot.finance.ledger.adapter.grpc.v1.TurnEnd;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Entered over the real Netty channel {@link AbstractSystemTest} binds, so a passing happy path also proves the
 * server binds and serves — the in-process transport {@code @GrpcAdapterTest} uses would have hidden that.
 */
class ExtractIntentsSystemTest extends AbstractSystemTest {

    private static final String CALLER_TOKEN = CallerTokens.bearer(1001L, "message-1001-1");

    /** The grouping the scenarios file under — one of the fixture's own, so no literal is repeated. */
    private static final String GROUPING = RequestFixtures.DEFAULT_CATEGORY_GROUPINGS.getFirst();

    private static String proposalArguments() {
        return """
                {"category":"Lunch","grouping":"%s","description":"lunch","amount":"15.00",\
                "currencyCode":"EUR","merchant":"Deli Co"}"""
                .formatted(GROUPING);
    }

    /** Waits for the one end the turn sends and returns it. */
    private static RecordedCall awaitTheEnd() {
        List<RecordedCall> ends = LedgerTurnStubs.awaitEnds(1);
        assertThat(ends).hasSize(1);
        return ends.getFirst();
    }

    private static TurnEnd endOf(RecordedCall call) {
        return ((EndTurnRequest) call.request()).getEnd();
    }

    private static String summarizeSpendingArguments(String from, String to) {
        return """
                {"from":"%s","to":"%s"}""".formatted(from, to);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a tokened request arrives - then the RPC answers empty and the ledger receives the "
                + "tool call under that token")
        void whenTokenedRequestArrives_thenRpcAnswersEmptyAndLedgerReceivesOneToolCallUnderToken() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-1", proposalArguments())),
                    ChatCompletionFixtures.textResponse("recorded"));

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                    .extractIntents(RequestFixtures.request());
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            awaitTheEnd();

            List<LoggedRequest> toolCalls = CapturedRequestUtils.toolCallRequests();
            assertThat(toolCalls).hasSize(1);
            JsonNode arguments = CapturedRequestUtils.toolCallArguments(toolCalls.getFirst());
            assertThat(arguments.get("category").asText()).isEqualTo("Lunch");
            assertThat(arguments.get("grouping").asText()).isEqualTo(GROUPING);
            assertThat(arguments.get("merchant").asText()).isEqualTo("Deli Co");
            assertThat(arguments.get("amount").asText()).isEqualTo("15.00");
            assertThat(arguments.get("currencyCode").asText()).isEqualTo("EUR");
            assertThat(toolCalls.getFirst().getHeader("Authorization")).isEqualTo(CALLER_TOKEN);
        }

        @Test
        @DisplayName("when the provider looks up a catalogued grouping - then only the proposal reaches the ledger "
                + "and the lookup counts")
        void whenProviderLooksUpCataloguedGroupingThenRecords_thenLedgerReceivesOnlyProposalAndLookupCountsAnswered() {
            String lookups = MeterName.CATEGORY_LOOKUPS.meterName();
            double before = sampleOrZero(scrape(actuatorPort), lookups, "outcome=\"answered\"");
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall(
                            "call-list-1",
                            ChatCompletionFixtures.LedgerTool.LIST_CATEGORIES,
                            "{\"grouping\":\"" + GROUPING + "\"}")),
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-2", proposalArguments())),
                    ChatCompletionFixtures.textResponse("recorded"));

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                    .extractIntents(RequestFixtures.requestWithCatalogue(
                            RequestFixtures.request().getText(),
                            List.of(RequestFixtures.catalogueEntry(GROUPING, "Lunch")),
                            GROUPING));
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            awaitTheEnd();

            assertThat(CapturedRequestUtils.toolCallRequests("list_categories")).isEmpty();
            List<LoggedRequest> createExpenseProposalCalls = CapturedRequestUtils.toolCallRequests();
            assertThat(createExpenseProposalCalls).hasSize(1);
            assertThat(createExpenseProposalCalls.getFirst().getHeader("Authorization"))
                    .isEqualTo(CALLER_TOKEN);
            assertThat(sampleOrZero(scrape(actuatorPort), lookups, "outcome=\"answered\""))
                    .isEqualTo(before + 1);
        }

        @Test
        @DisplayName("when the provider asks to summarize spending - then that call reaches the ledger under the "
                + "request's token")
        void whenProviderAsksToSummarizeSpending_thenThatCallReachesLedgerUnderToken() {
            String from = "2026-08-01";
            String to = RequestFixtures.DEFAULT_CURRENT_DATE;
            McpLedgerStubs.stubSummarizeSpendingAccepted(from, to);
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall(
                            "call-summarize-1",
                            ChatCompletionFixtures.LedgerTool.SUMMARIZE_SPENDING,
                            summarizeSpendingArguments(from, to))),
                    ChatCompletionFixtures.textResponse("you spent 15 euros"));

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                    .extractIntents(RequestFixtures.request());
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            awaitTheEnd();

            List<LoggedRequest> summarizeSpendingCalls = CapturedRequestUtils.toolCallRequests("summarize_spending");
            assertThat(summarizeSpendingCalls).hasSize(1);
            assertThat(summarizeSpendingCalls.getFirst().getHeader("Authorization"))
                    .isEqualTo(CALLER_TOKEN);
            JsonNode arguments = CapturedRequestUtils.toolCallArguments(summarizeSpendingCalls.getFirst());
            assertThat(arguments.get("from").asText()).isEqualTo(from);
            assertThat(arguments.get("to").asText()).isEqualTo(to);
        }

        @Test
        @DisplayName("when the mode is unset - then the classifier receives no request and the turn counts under "
                + "tools and acted_on")
        void whenModeUnset_thenClassifierReceivesNoRequestAndToolsTurnCounts() {
            String turns = MeterName.TURNS.meterName();
            double before = toolsActedOnTurns(scrape(actuatorPort), turns);
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-1", proposalArguments())),
                    ChatCompletionFixtures.textResponse("recorded"));

            AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                    .extractIntents(RequestFixtures.request());
            awaitTheEnd();

            assertThat(WireMockSupport.SERVER.findAll(
                            postRequestedFor(urlPathEqualTo(ClassifierStubs.SYSTEM_ONE_PATH))))
                    .isEmpty();
            assertThat(toolsActedOnTurns(scrape(actuatorPort), turns)).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("when the provider answers after a second - then the call answers empty first and the turn "
                + "ends acted on under the token")
        void whenProviderAnswersAfterOneSecond_thenCallAnswersEmptyBeforeProviderAndEndsActedOnUnderToken() {
            WireMockStubs.stubChatCompletionDelayed(
                    ChatCompletionFixtures.textResponse("nothing to record"), Duration.ofSeconds(1));

            long startedAt = System.nanoTime();
            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                    .extractIntents(RequestFixtures.request());
            Duration took = Duration.ofNanos(System.nanoTime() - startedAt);
            log.info("response: {} after {}", response, took);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            assertThat(took).isLessThan(Duration.ofSeconds(1));

            RecordedCall end = awaitTheEnd();
            assertThat(endOf(end)).isEqualTo(TurnEnd.TURN_END_ACTED_ON);
            assertThat(end.authorization()).isEqualTo(CALLER_TOKEN);
        }

        private double toolsActedOnTurns(String body, String metric) {
            return sampleOrZero(body, metric, "mode=\"tools\"", "outcome=\"acted_on\"");
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the provider returns a server error - then the call answers empty and the turn "
                + "ends unavailable under the token")
        void whenProviderRespondsServerError_thenCallAnswersEmptyAndTurnEndsUnavailable() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionServerError();

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                    .extractIntents(RequestFixtures.request());
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            RecordedCall end = awaitTheEnd();
            assertThat(endOf(end)).isEqualTo(TurnEnd.TURN_END_UNAVAILABLE);
            assertThat(end.authorization()).isEqualTo(CALLER_TOKEN);
        }

        @Test
        @DisplayName("when a request carries no authorization metadata - then it fails with UNAUTHENTICATED, the "
                + "provider never called")
        void whenRequestCarriesNoAuthorizationMetadata_thenFailsWithUnauthenticatedAndProviderNeverCalled() {
            assertThatThrownBy(() -> intentExtractionStub.extractIntents(RequestFixtures.request()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);

            assertThat(CapturedRequestUtils.chatCompletionRequests()).isEmpty();
        }
    }
}
