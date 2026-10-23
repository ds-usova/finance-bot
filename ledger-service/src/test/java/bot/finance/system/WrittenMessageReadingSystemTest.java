package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.ExpenseEntity;
import bot.finance.adapter.persistence.WrittenMessageEntity;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.port.UserRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.ProposalReportRowUtils;
import bot.finance.common.rows.WrittenMessageRowUtils;
import bot.finance.domain.model.User;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Grouping;
import bot.finance.domain.value.WrittenMessageStatus;
import bot.finance.ledger.adapter.grpc.v1.TurnEnd;
import io.grpc.Status;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/** Covers the reader picking up a stored waiting message on its own schedule, with no request involved. */
class WrittenMessageReadingSystemTest extends AbstractSystemTest {

    private static final String MESSAGE_TEXT = "coffee 3.40, taxi home 14";
    private static final String CATEGORY = "Supermarkets";
    private static final String GROUPING = "Groceries";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when the schedule comes round to a stored waiting message - then it is read to DONE with entries")
        void whenTheScheduleComesRoundToAStoredWaitingMessage_thenItIsReadToDoneWithEntries() {
            // given: a waiting message stored for a person, and the connector armed to record one proposal per expense
            long userId = userRepository
                    .create(User.newUser("written-message-reading-happy-user"), Grouping.defaults())
                    .id()
                    .orElseThrow();
            long messageId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, MESSAGE_TEXT, WrittenMessageStatus.WAITING, null, Instant.now());
            GrpcStubServer.armMcpCallbacks(
                    "http://localhost:" + port,
                    McpRequests.createExpenseProposal(CATEGORY, GROUPING, "coffee", "Cafe", "3.40", "EUR"),
                    McpRequests.createExpenseProposal(CATEGORY, GROUPING, "taxi home", "Taxi", "14", "EUR"));

            // when: the reader's schedule comes round
            await("the message is read to DONE")
                    .atMost(TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(
                            () -> assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId)
                                            .status())
                                    .as("status of message %s", messageId)
                                    .isEqualTo("DONE"));

            // then: it is settled RECORDED
            WrittenMessageEntity settled = WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId);
            assertThat(settled.outcome()).as("outcome").isEqualTo(ReportOutcome.RECORDED.name());

            // then: two pending entries are stored under the message
            List<ExpenseEntity> entries =
                    ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId, ExpenseStatus.PENDING);
            assertThat(entries).as("PENDING entries of the person").hasSize(2).allSatisfy(entry -> assertThat(
                            entry.incomingMessageId())
                    .as("the message the entry is filed under")
                    .isEqualTo("web:" + messageId));

            // then: no proposal report is stored for the person
            assertThat(ProposalReportRowUtils.proposalReportRowsFor(jdbcAggregateTemplate, userId))
                    .as("proposal reports of the person")
                    .isEmpty();
        }

        @Test
        @DisplayName("when the connector withholds the turn's end - then the message is READING and stays unsettled")
        void whenTheConnectorWithholdsTheTurnsEnd_thenTheMessageIsReadingAndStaysUnsettled() {
            // given: a waiting message stored for a person, and a connector that withholds the turn's end
            long userId = userRepository
                    .create(User.newUser("written-message-reading-withheld-user"), Grouping.defaults())
                    .id()
                    .orElseThrow();
            long messageId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, MESSAGE_TEXT, WrittenMessageStatus.WAITING, null, Instant.now());
            GrpcStubServer.withholdTurnEnd();

            // when: the reader's schedule comes round
            await("the message is handed over and READING")
                    .atMost(TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(
                            () -> assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId)
                                            .status())
                                    .as("status of message %s", messageId)
                                    .isEqualTo("READING"));

            // then: it stays READING, with no outcome
            await("the message stays unsettled")
                    .during(Duration.ofSeconds(3))
                    .atMost(TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> {
                        WrittenMessageEntity row =
                                WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId);
                        assertThat(row.status())
                                .as("status of message %s", messageId)
                                .isEqualTo("READING");
                        assertThat(row.outcome())
                                .as("outcome of message %s", messageId)
                                .isNull();
                    });
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the connector fails every extraction - then the message is settled FAILED with no entry")
        void whenTheConnectorFailsEveryExtraction_thenTheMessageIsSettledFailedWithNoEntry() {
            // given: a waiting message stored for a person, and a connector that accepts the hand-over and ends the
            // turn failed
            long userId = userRepository
                    .create(User.newUser("written-message-reading-failing-user"), Grouping.defaults())
                    .id()
                    .orElseThrow();
            long messageId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, MESSAGE_TEXT, WrittenMessageStatus.WAITING, null, Instant.now());
            GrpcStubServer.endTurnWith(TurnEnd.TURN_END_FAILED);

            // when: the reader's schedule comes round
            await("the message is settled")
                    .atMost(TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(
                            () -> assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId)
                                            .status())
                                    .as("status of message %s", messageId)
                                    .isEqualTo("DONE"));

            // then: it is settled FAILED
            WrittenMessageEntity settled = WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId);
            assertThat(settled.outcome()).as("outcome").isEqualTo(ReportOutcome.FAILED.name());

            // then: no entry is stored under the message
            assertThat(ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId))
                    .as("entries of the person")
                    .noneSatisfy(entry -> assertThat(entry.incomingMessageId()).isEqualTo("web:" + messageId));
        }

        @Test
        @DisplayName("when every hand-over fails as unavailable - then the message stays WAITING with its update time "
                + "moved on")
        void whenEveryHandOverFailsAsUnavailable_thenTheMessageStaysWaitingWithItsUpdateTimeMovedOn() {
            // given: a waiting message stored for a person, and a connector failing every hand-over
            long userId = userRepository
                    .create(User.newUser("written-message-reading-unavailable-user"), Grouping.defaults())
                    .id()
                    .orElseThrow();
            long messageId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, MESSAGE_TEXT, WrittenMessageStatus.WAITING, null, Instant.now());
            GrpcStubServer.failExtractionWith(Status.UNAVAILABLE.withDescription("AI connector unavailable"));

            try {
                // when: the reader's schedule comes round twice
                await("the first attempt moves the message's update time past its filing")
                        .atMost(TIMEOUT)
                        .pollInterval(POLL_INTERVAL)
                        .untilAsserted(() -> {
                            WrittenMessageEntity row =
                                    WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId);
                            assertThat(row.updatedAt())
                                    .as("updated_at of message %s", messageId)
                                    .isAfter(row.createdAt());
                        });
                Instant afterFirstAttempt = WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId)
                        .updatedAt();
                await("the second attempt moves it again")
                        .atMost(TIMEOUT)
                        .pollInterval(POLL_INTERVAL)
                        .untilAsserted(() -> assertThat(
                                        WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId)
                                                .updatedAt())
                                .as("updated_at of message %s", messageId)
                                .isAfter(afterFirstAttempt));

                // then: the message is still WAITING
                WrittenMessageEntity row = WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId);
                assertThat(row.status()).as("status of message %s", messageId).isEqualTo("WAITING");
                assertThat(row.outcome()).as("outcome of message %s", messageId).isNull();
            } finally {
                jdbcAggregateTemplate.deleteById(messageId, WrittenMessageEntity.class);
            }
        }

        @Test
        @DisplayName("when a waiting message was filed and last touched an hour ago - then it is settled FAILED "
                + "without a hand-over")
        void whenAWaitingMessageWasFiledAndLastTouchedAnHourAgo_thenItIsSettledFailedWithoutAHandOver() {
            // given: a waiting message filed an hour ago and touched an hour ago, with nothing under it
            String text = "sweep-me " + MESSAGE_TEXT;
            Instant anHourAgo = Instant.now().minus(Duration.ofHours(1));
            long userId = userRepository
                    .create(User.newUser("written-message-reading-stale-user"), Grouping.defaults())
                    .id()
                    .orElseThrow();
            long messageId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, text, WrittenMessageStatus.WAITING, null, anHourAgo, anHourAgo);

            // when: the reader's schedule comes round
            await("the message is settled")
                    .atMost(TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(
                            () -> assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId)
                                            .status())
                                    .as("status of message %s", messageId)
                                    .isEqualTo("DONE"));

            // then: it is settled FAILED
            assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId)
                            .outcome())
                    .as("outcome")
                    .isEqualTo(ReportOutcome.FAILED.name());

            // then: it was never handed over to the connector
            assertThat(Optional.ofNullable(GrpcStubServer.lastExtractionRequest())
                            .map(ExtractIntentsRequest::getText))
                    .as("text of the last hand-over the connector received")
                    .isNotEqualTo(Optional.of(text));
        }
    }
}
