package bot.finance.ai.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.common.boot.AbstractSystemTest;
import bot.finance.ai.common.containers.LedgerTurnServer.RecordedCall;
import bot.finance.ai.common.fixtures.CallerTokens;
import bot.finance.ai.common.fixtures.ChatCompletionFixtures;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.stubs.AuthorizedStubs;
import bot.finance.ai.common.stubs.CapturedRequestUtils;
import bot.finance.ai.common.stubs.LedgerTurnStubs;
import bot.finance.ai.common.stubs.McpLedgerStubs;
import bot.finance.ai.common.stubs.WireMockStubs;
import bot.finance.ledger.adapter.grpc.v1.EndTurnRequest;
import bot.finance.ledger.adapter.grpc.v1.TurnEnd;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Entered over the real Netty channel with room for one turn and a three-second bound on it, so a second turn and a
 * slow provider both meet a limit within a test's reach. Each test awaits its turn's end before it ends, so no turn
 * runs on into the next test.
 */
@TestPropertySource(properties = {"extraction.max-turns=1", "extraction.turn-timeout=3s"})
class TurnRunSystemTest extends AbstractSystemTest {

    private static final String GROUPING = RequestFixtures.DEFAULT_CATEGORY_GROUPINGS.getFirst();

    private static String proposalArguments() {
        return """
                {"category":"Lunch","grouping":"%s","description":"lunch","amount":"15.00",\
                "currencyCode":"EUR","merchant":"Deli Co"}"""
                .formatted(GROUPING);
    }

    private static TurnEnd endOf(RecordedCall call) {
        return ((EndTurnRequest) call.request()).getEnd();
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when the same token is handed over twice while the first turn runs - then both answer empty "
                + "and one turn runs")
        void whenSameTokenHandedOverTwiceWhileFirstTurnRuns_thenBothAnswerEmptyAndOneTurnRuns() {
            String token = CallerTokens.bearer(3001L, "message-3001-1");
            WireMockStubs.stubChatCompletionDelayed(
                    ChatCompletionFixtures.textResponse("nothing to record"), Duration.ofSeconds(1));

            ExtractIntentsResponse first = AuthorizedStubs.withCallerToken(intentExtractionStub, token)
                    .extractIntents(RequestFixtures.request());
            ExtractIntentsResponse second = AuthorizedStubs.withCallerToken(intentExtractionStub, token)
                    .extractIntents(RequestFixtures.request());
            log.info("responses: {} {}", first, second);

            assertThat(first).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            assertThat(second).isEqualTo(ExtractIntentsResponse.getDefaultInstance());

            LedgerTurnStubs.awaitEnds(1);
            await().during(Duration.ofMillis(500))
                    .atMost(Duration.ofSeconds(5))
                    .until(() -> LedgerTurnStubs.recordedEnds().size() == 1);
            assertThat(CapturedRequestUtils.chatCompletionRequests()).hasSize(1);
        }

        @Test
        @DisplayName("when the call carries a deadline of 500 ms - then it answers empty within it and the turn "
                + "ends acted on after")
        void whenCallCarriesShortDeadline_thenAnswersEmptyWithinItAndTurnEndsActedOnAfter() {
            String token = CallerTokens.bearer(3002L, "message-3002-1");
            WireMockStubs.stubChatCompletionDelayed(
                    ChatCompletionFixtures.textResponse("nothing to record"), Duration.ofSeconds(1));

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(
                            intentExtractionStub.withDeadlineAfter(500, TimeUnit.MILLISECONDS), token)
                    .extractIntents(RequestFixtures.request());
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            assertThat(LedgerTurnStubs.recordedEnds()).isEmpty();

            List<RecordedCall> ends = LedgerTurnStubs.awaitEnds(1);
            assertThat(ends).hasSize(1);
            assertThat(endOf(ends.getFirst())).isEqualTo(TurnEnd.TURN_END_ACTED_ON);
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the provider answers a tool call after six seconds - then the turn ends unavailable and "
                + "no tool call reaches the ledger")
        void whenProviderAnswersToolCallAfterSixSeconds_thenTurnEndsUnavailableAndNoToolCallReachesLedger() {
            String token = CallerTokens.bearer(3003L, "message-3003-1");
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionDelayed(
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-1", proposalArguments())),
                    Duration.ofSeconds(6));

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, token)
                    .extractIntents(RequestFixtures.request());
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            List<RecordedCall> ends = LedgerTurnStubs.awaitEnds(1);
            assertThat(ends).hasSize(1);
            assertThat(endOf(ends.getFirst())).isEqualTo(TurnEnd.TURN_END_UNAVAILABLE);
            assertThat(ends.getFirst().authorization()).isEqualTo(token);

            await().during(Duration.ofSeconds(4))
                    .atMost(Duration.ofSeconds(8))
                    .until(() -> CapturedRequestUtils.toolCallRequests().isEmpty());
        }
    }
}
