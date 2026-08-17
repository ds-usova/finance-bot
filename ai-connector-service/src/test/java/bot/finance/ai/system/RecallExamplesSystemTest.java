package bot.finance.ai.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.common.boot.AbstractMemorySystemTest;
import bot.finance.ai.common.fixtures.CallerTokens;
import bot.finance.ai.common.fixtures.ChatCompletionFixtures;
import bot.finance.ai.common.fixtures.EmbeddingFixtures;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.rows.IncomingMessageRowUtils;
import bot.finance.ai.common.rows.RecordedExpenseRowUtils;
import bot.finance.ai.common.stubs.AuthorizedStubs;
import bot.finance.ai.common.stubs.CapturedRequestUtils;
import bot.finance.ai.common.stubs.McpLedgerStubs;
import bot.finance.ai.common.stubs.WireMockStubs;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Entered over the real Netty channel {@link AbstractMemorySystemTest} binds, with the memory on against the real,
 * containerized database. {@code ExtractIntentsUseCase} recalls examples through {@code RecallExamplesPort} before
 * delegating to the recording adapter.
 */
class RecallExamplesSystemTest extends AbstractMemorySystemTest {

    private static final String GROUPING = "Dining";
    private static final String CATEGORY_NAME = "Restaurants";
    private static final String NEW_TEXT = "spent 15 euros on lunch";

    private static String proposalArguments() {
        return """
                {"category":"Lunch","grouping":"%s","description":"lunch","amount":"15.00",\
                "currencyCode":"EUR","merchant":"Deli Co"}"""
                .formatted(RequestFixtures.DEFAULT_CATEGORY_GROUPINGS.getFirst());
    }

    private static void stubOneTurn() {
        McpLedgerStubs.stubCreateExpenseProposalAccepted();
        WireMockStubs.stubChatCompletionSequence(
                ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1", proposalArguments())),
                ChatCompletionFixtures.textResponse("recorded"));
    }

    private long insertEarlierAcceptedMessage(long userId, String incomingMessageId, String text, List<Float> vector) {
        long messageId = IncomingMessageRowUtils.insertWithVectorReturningId(
                jdbcTemplate, userId, incomingMessageId, text, Instant.now().minus(Duration.ofDays(1)), vector, 0);
        RecordedExpenseRowUtils.insertDecided(
                jdbcTemplate,
                messageId,
                userId,
                messageId * 10,
                "lunch",
                "Deli Co",
                1500L,
                "EUR",
                42L,
                CATEGORY_NAME,
                GROUPING,
                "ACCEPTED");
        return messageId;
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a new message arrives close to an earlier accepted one - then the prompt carries it as "
                + "an example")
        void whenNewMessageArrivesCloseToEarlierAcceptedOne_thenPromptCarriesItAsExample() {
            long userId = 7001L;
            String earlierIncomingMessageId = "message-7001-earlier";
            String newIncomingMessageId = "message-7001-new";
            String earlierText = "spent 15 on lunch and 3.50 coffee";
            insertEarlierAcceptedMessage(
                    userId, earlierIncomingMessageId, earlierText, EmbeddingFixtures.unitVector(0));

            WireMockStubs.stubEmbeddings(EmbeddingFixtures.embeddingsResponse(EmbeddingFixtures.unitVectorAt(0, 0.9)));
            stubOneTurn();

            String token = CallerTokens.bearer(userId, newIncomingMessageId);
            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, token)
                    .extractIntents(RequestFixtures.request(NEW_TEXT));
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            assertThat(IncomingMessageRowUtils.vector(jdbcTemplate, userId, newIncomingMessageId))
                    .isNotEmpty();

            List<LoggedRequest> chatRequests = CapturedRequestUtils.chatCompletionRequests();
            assertThat(chatRequests).isNotEmpty();
            JsonNode firstRequestBody = CapturedRequestUtils.body(chatRequests.getFirst());
            String userMessageContent = CapturedRequestUtils.messageContent(firstRequestBody, "user");
            log.info("user message content: {}", userMessageContent);
            assertThat(userMessageContent)
                    .contains(earlierText)
                    .contains(CATEGORY_NAME)
                    .contains("accepted");
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the embeddings call fails - then the new row holds no vector and the prompt carries no "
                + "examples heading")
        void whenEmbeddingsCallFails_thenNewRowHoldsNoVectorAndPromptCarriesNoExamplesHeading() {
            long userId = 7002L;
            String earlierIncomingMessageId = "message-7002-earlier";
            String newIncomingMessageId = "message-7002-new";
            String earlierText = "spent 15 on lunch and 3.50 coffee";
            insertEarlierAcceptedMessage(
                    userId, earlierIncomingMessageId, earlierText, EmbeddingFixtures.unitVector(0));

            WireMockStubs.stubEmbeddingsServerError();
            stubOneTurn();

            String token = CallerTokens.bearer(userId, newIncomingMessageId);
            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, token)
                    .extractIntents(RequestFixtures.request(NEW_TEXT));
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            assertThat(IncomingMessageRowUtils.vector(jdbcTemplate, userId, newIncomingMessageId))
                    .isEmpty();
            assertThat(IncomingMessageRowUtils.embeddingAttempts(jdbcTemplate, userId, newIncomingMessageId))
                    .isGreaterThanOrEqualTo(1);

            List<LoggedRequest> chatRequests = CapturedRequestUtils.chatCompletionRequests();
            assertThat(chatRequests).isNotEmpty();
            JsonNode firstRequestBody = CapturedRequestUtils.body(chatRequests.getFirst());
            String userMessageContent = CapturedRequestUtils.messageContent(firstRequestBody, "user");
            log.info("user message content: {}", userMessageContent);
            assertThat(userMessageContent).doesNotContain("How this person's earlier messages were recorded");
        }
    }
}
