package bot.finance.ai.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.common.boot.AbstractMemorySystemTest;
import bot.finance.ai.common.fixtures.EmbeddingFixtures;
import bot.finance.ai.common.rows.IncomingMessageRowUtils;
import bot.finance.ai.common.rows.RecordedExpenseRowUtils;
import bot.finance.ai.common.rows.RecordedExpenseRowUtils.RecordedExpenseRow;
import bot.finance.ai.common.stubs.CapturedRequestUtils;
import bot.finance.ai.common.stubs.WireMockStubs;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Entered by letting {@code MemoryPurgeScheduler} fire on its own timer — {@link AbstractMemorySystemTest} sets
 * {@code memory.purge-interval} to one second, short enough to observe within a test — never by calling
 * {@code run()} directly. The scheduler now calls {@code BackfillEmbeddingsPort} after the purge.
 */
class BackfillEmbeddingsSystemTest extends AbstractMemorySystemTest {

    private static final Duration BOUND = Duration.ofSeconds(10);

    private long insertUnembedded(long userId, String incomingMessageId, String text) {
        return IncomingMessageRowUtils.insertReturningId(jdbcTemplate, userId, incomingMessageId, text, Instant.now());
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when the purge timer fires - then both unembedded rows hold a vector and their expenses are "
                + "untouched")
        void whenPurgeTimerFires_thenBothUnembeddedRowsHoldVectorAndExpensesUntouched() {
            long userId = 8001L;
            String firstIncomingMessageId = "message-8001-first";
            String secondIncomingMessageId = "message-8001-second";
            String firstText = "spent 15 euros on lunch";
            String secondText = "spent 3.50 on coffee";
            long firstMessageId = insertUnembedded(userId, firstIncomingMessageId, firstText);
            insertUnembedded(userId, secondIncomingMessageId, secondText);
            long proposalId = 80011L;
            RecordedExpenseRowUtils.insertDecided(
                    jdbcTemplate,
                    firstMessageId,
                    userId,
                    proposalId,
                    "lunch",
                    "Deli Co",
                    1500L,
                    "EUR",
                    42L,
                    "Restaurants",
                    "Dining",
                    "ACCEPTED");

            WireMockStubs.stubEmbeddings(EmbeddingFixtures.embeddingsResponseForAll(
                    List.of(EmbeddingFixtures.unitVector(0), EmbeddingFixtures.unitVector(1))));

            Awaitility.await().atMost(BOUND).untilAsserted(() -> {
                List<Float> firstVector = IncomingMessageRowUtils.vector(jdbcTemplate, userId, firstIncomingMessageId);
                List<Float> secondVector =
                        IncomingMessageRowUtils.vector(jdbcTemplate, userId, secondIncomingMessageId);
                log.info("first vector size: {}, second vector size: {}", firstVector.size(), secondVector.size());
                assertThat(firstVector).isNotEmpty();
                assertThat(secondVector).isNotEmpty();
            });

            RecordedExpenseRow recordedExpenseRow = RecordedExpenseRowUtils.findByProposalId(jdbcTemplate, proposalId)
                    .orElseThrow();
            log.info("recorded expense row: {}", recordedExpenseRow);
            assertThat(recordedExpenseRow.status()).isEqualTo("ACCEPTED");
            assertThat(recordedExpenseRow.categoryName()).isEqualTo("Restaurants");
            assertThat(recordedExpenseRow.groupingName()).isEqualTo("Dining");

            List<LoggedRequest> embeddingsRequests = CapturedRequestUtils.embeddingsRequests();
            assertThat(embeddingsRequests).isNotEmpty();
            String requestBody = embeddingsRequests.getFirst().getBodyAsString();
            log.info("embeddings request body: {}", requestBody);
            assertThat(requestBody).contains(firstText).contains(secondText);
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the purge timer fires and embeddings fail - then the row holds no vector, an attempt, "
                + "and stays")
        void whenPurgeTimerFiresAndEmbeddingsFail_thenRowHoldsNoVectorAttemptAndStays() {
            long userId = 8002L;
            String incomingMessageId = "message-8002-unembeddable";
            insertUnembedded(userId, incomingMessageId, "spent 15 euros on lunch");

            WireMockStubs.stubEmbeddingsServerError();

            Awaitility.await().atMost(BOUND).untilAsserted(() -> {
                int attempts = IncomingMessageRowUtils.embeddingAttempts(jdbcTemplate, userId, incomingMessageId);
                log.info("embedding attempts: {}", attempts);
                assertThat(attempts).isGreaterThanOrEqualTo(1);
            });

            assertThat(IncomingMessageRowUtils.vector(jdbcTemplate, userId, incomingMessageId))
                    .isEmpty();
            assertThat(IncomingMessageRowUtils.count(jdbcTemplate, userId, incomingMessageId))
                    .isEqualTo(1);
        }
    }
}
