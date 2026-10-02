package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.ExpenseEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.adapter.persistence.WrittenMessageEntity;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.ProposalReportRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.common.rows.WrittenMessageRowUtils;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.WrittenMessageStatus;
import io.grpc.Status;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when the schedule comes round to a stored waiting message - then it is read to DONE with entries")
        void whenTheScheduleComesRoundToAStoredWaitingMessage_thenItIsReadToDoneWithEntries() {
            // given: a waiting message stored for a person, and the connector armed to record one proposal per expense
            long userId = UserRowUtils.storedUserId(userEntityRepository, "written-message-reading-happy-user");
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
                    .untilAsserted(() -> assertThat(
                                    WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId)
                                            .status())
                            .as("status of message %s", messageId)
                            .isEqualTo("DONE"));

            // then: it is settled RECORDED
            WrittenMessageEntity settled = WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId);
            assertThat(settled.outcome()).as("outcome").isEqualTo(ReportOutcome.RECORDED.name());

            // then: two pending entries are stored under the message
            List<ExpenseEntity> entries =
                    ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId, ExpenseStatus.PENDING);
            assertThat(entries)
                    .as("PENDING entries of the person")
                    .hasSize(2)
                    .allSatisfy(entry -> assertThat(entry.incomingMessageId())
                            .as("the message the entry is filed under")
                            .isEqualTo("web:" + messageId));

            // then: no proposal report is stored for the person
            assertThat(ProposalReportRowUtils.proposalReportRowsFor(jdbcAggregateTemplate, userId))
                    .as("proposal reports of the person")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the connector fails every extraction - then the message is settled FAILED with no entry")
        void whenTheConnectorFailsEveryExtraction_thenTheMessageIsSettledFailedWithNoEntry() {
            // given: a waiting message stored for a person, and a connector that is unavailable
            long userId = UserRowUtils.storedUserId(userEntityRepository, "written-message-reading-failing-user");
            long messageId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, MESSAGE_TEXT, WrittenMessageStatus.WAITING, null, Instant.now());
            GrpcStubServer.failExtractionWith(Status.UNAVAILABLE.withDescription("AI connector unavailable"));

            // when: the reader's schedule comes round
            await("the message is settled")
                    .atMost(TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(
                                    WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, messageId)
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
    }
}
