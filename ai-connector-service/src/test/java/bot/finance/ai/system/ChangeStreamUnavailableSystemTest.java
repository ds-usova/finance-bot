package bot.finance.ai.system;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.common.boot.AbstractMemorySystemTest;
import bot.finance.ai.common.fixtures.CallerTokens;
import bot.finance.ai.common.fixtures.ChatCompletionFixtures;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.rows.IncomingMessageRowUtils;
import bot.finance.ai.common.stubs.AuthorizedStubs;
import bot.finance.ai.common.stubs.McpLedgerStubs;
import bot.finance.ai.common.stubs.WireMockStubs;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Points its own context at a Redis nobody listens on, overriding {@link AbstractMemorySystemTest}'s registrar
 * with {@link UnreachableRedisConfiguration} — the shape ledger-service's {@code CdcCaptureTest} uses. A turn is
 * entered over the real Netty channel {@link bot.finance.ai.common.boot.AbstractSystemTest} binds; the health
 * check is entered over the actuator port.
 */
@Import(ChangeStreamUnavailableSystemTest.UnreachableRedisConfiguration.class)
class ChangeStreamUnavailableSystemTest extends AbstractMemorySystemTest {

    private static final String GROUPING = RequestFixtures.DEFAULT_CATEGORY_GROUPINGS.getFirst();

    private static String proposalArguments() {
        return """
                {"category":"Lunch","grouping":"%s","description":"lunch","amount":"15.00",\
                "currencyCode":"EUR","merchant":"Deli Co"}"""
                .formatted(GROUPING);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a turn runs with the ledger's Redis unreachable - then it answers empty and one row "
                + "holds the request's text")
        void whenTurnRunsWithRedisUnreachable_thenAnswersEmptyAndOneRowHoldsText() {
            long userId = 9401L;
            String incomingMessageId = "message-9401-1";
            String text = "spent 15 euros on lunch";
            String token = CallerTokens.bearer(userId, incomingMessageId);
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-1", proposalArguments())),
                    ChatCompletionFixtures.textResponse("recorded"));

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, token)
                    .extractIntents(RequestFixtures.request(text));
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            assertThat(IncomingMessageRowUtils.count(jdbcTemplate, userId, incomingMessageId))
                    .isEqualTo(1);
            assertThat(IncomingMessageRowUtils.text(jdbcTemplate, userId, incomingMessageId))
                    .isEqualTo(text);
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when GET /actuator/health is called with Redis unreachable - then it answers 503, DOWN, "
                + "redis DOWN and db UP")
        void whenActuatorHealthCalledWithRedisUnreachable_thenAnswers503DownRedisDownDbUp() {
            Response response = given().port(actuatorPort).when().get("/actuator/health");
            log.info("response: {}", response.getBody().asString());

            response.then().statusCode(503);
            assertThat(response.jsonPath().getString("status")).isEqualTo("DOWN");
            assertThat(response.jsonPath().getString("components.redis.status")).isEqualTo("DOWN");
            assertThat(response.jsonPath().getString("components.db.status")).isEqualTo("UP");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UnreachableRedisConfiguration {

        @Bean
        DynamicPropertyRegistrar unreachableRedisProperties() {
            return registry -> registry.add("spring.data.redis.url", () -> "redis://localhost:1");
        }
    }
}
