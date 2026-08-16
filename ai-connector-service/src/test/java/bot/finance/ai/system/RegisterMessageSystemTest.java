package bot.finance.ai.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.common.boot.AbstractMemorySystemTest;
import bot.finance.ai.common.fixtures.CallerTokens;
import bot.finance.ai.common.fixtures.ChatCompletionFixtures;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.rows.IncomingMessageRowUtils;
import bot.finance.ai.common.stubs.AuthorizedStubs;
import bot.finance.ai.common.stubs.CapturedRequestUtils;
import bot.finance.ai.common.stubs.McpLedgerStubs;
import bot.finance.ai.common.stubs.WireMockStubs;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Entered over the real Netty channel {@link AbstractMemorySystemTest} binds, with the memory on against the
 * real, containerized database.
 */
class RegisterMessageSystemTest extends AbstractMemorySystemTest {

    private static final String TEXT = "spent 15 euros on lunch";
    private static final String GROUPING = RequestFixtures.DEFAULT_CATEGORY_GROUPINGS.getFirst();

    private static String proposalArguments() {
        return """
                {"category":"Lunch","grouping":"%s","description":"lunch","amount":"15.00",\
                "currencyCode":"EUR","merchant":"Deli Co"}"""
                .formatted(GROUPING);
    }

    private static void stubOneTurn() {
        McpLedgerStubs.stubCreateExpenseProposalAccepted();
        WireMockStubs.stubChatCompletionSequence(
                ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1", proposalArguments())),
                ChatCompletionFixtures.textResponse("recorded"));
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a tokened request registers a new message - then it is stored under the token's identity")
        void whenTokenedRequestRegistersNewMessage_thenStoredUnderTokenIdentity() {
            long userId = 4001L;
            String incomingMessageId = "message-4001-1";
            String token = CallerTokens.bearer(userId, incomingMessageId);
            stubOneTurn();

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, token)
                    .extractIntents(RequestFixtures.request(TEXT));
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            assertThat(CapturedRequestUtils.toolCallRequests()).hasSize(1);
            assertThat(CapturedRequestUtils.toolCallRequests().getFirst().getHeader("Authorization"))
                    .isEqualTo(token);
            assertThat(IncomingMessageRowUtils.count(jdbcTemplate, userId, incomingMessageId))
                    .isEqualTo(1);
            assertThat(IncomingMessageRowUtils.text(jdbcTemplate, userId, incomingMessageId))
                    .isEqualTo(TEXT);
        }

        @Test
        @DisplayName("when the same message is registered twice - then it is stored only once")
        void whenSameMessageRegisteredTwice_thenStoredOnlyOnce() {
            long userId = 4002L;
            String incomingMessageId = "message-4002-1";
            stubOneTurn();
            AuthorizedStubs.withCallerToken(intentExtractionStub, CallerTokens.bearer(userId, incomingMessageId))
                    .extractIntents(RequestFixtures.request(TEXT));

            stubOneTurn();
            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(
                            intentExtractionStub, CallerTokens.bearer(userId, incomingMessageId))
                    .extractIntents(RequestFixtures.request(TEXT));
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            assertThat(IncomingMessageRowUtils.count(jdbcTemplate, userId, incomingMessageId))
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the token is signed by an unpublished key - then it fails with UNAUTHENTICATED")
        void whenTokenSignedByUnpublishedKey_thenFailsUnauthenticated() {
            long userId = 4003L;
            String incomingMessageId = "message-4003-1";

            assertThatThrownBy(() -> AuthorizedStubs.withCallerToken(
                                    intentExtractionStub,
                                    CallerTokens.bearerSignedByUnpublishedKey(userId, incomingMessageId))
                            .extractIntents(RequestFixtures.request(TEXT)))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);

            assertThat(CapturedRequestUtils.chatCompletionRequests()).isEmpty();
            assertThat(IncomingMessageRowUtils.count(jdbcTemplate, userId, incomingMessageId))
                    .isEqualTo(0);
        }
    }
}
