package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.ProposalReportRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.model.ProposalReport;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@PersistenceAdapterTest
@Import(ProposalReportRepositoryAdapter.class)
class ProposalReportRepositoryAdapterTest {

    @Autowired
    private ProposalReportRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("storing a proposal report")
    class Store {

        @Test
        @DisplayName("when a report is stored - then the row holds its fields and the answer carries the generated id")
        void whenReportIsStored_thenRowHoldsFieldsAndAnswerCarriesGeneratedId() {
            long userId = storedUserId("store-report-user");
            IncomingMessageId incomingMessageId =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            ProposalReport report =
                    ProposalReport.newProposalReport(userId, incomingMessageId, "conversation-1", "sent-message-1");

            ProposalReport stored = adapter.store(report);

            assertThat(stored.id()).isPresent();
            assertThat(reportRowsFor(userId)).singleElement().satisfies(row -> {
                assertThat(row.conversationId()).isEqualTo("conversation-1");
                assertThat(row.sentMessageId()).isEqualTo("sent-message-1");
                assertThat(row.incomingMessageId()).isEqualTo(incomingMessageId.value());
            });
        }

        @Test
        @DisplayName(
                "when a second report for the same message uses a different sent message id - then both inserts succeed")
        void whenSecondReportForSameMessageUsesDifferentSentMessageId_thenBothInsertsSucceed() {
            long userId = storedUserId("store-report-twice-user");
            IncomingMessageId incomingMessageId =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            adapter.store(
                    ProposalReport.newProposalReport(userId, incomingMessageId, "conversation-1", "sent-message-1"));

            adapter.store(
                    ProposalReport.newProposalReport(userId, incomingMessageId, "conversation-1", "sent-message-2"));

            assertThat(reportRowsFor(userId)).hasSize(2);
        }

        @Test
        @DisplayName("when the user id names no stored person - then throws EntityNotFoundException")
        void whenUserIdNamesNoStoredPerson_thenThrowsEntityNotFoundException() {
            long unknownUserId = 999_999_999L;
            ProposalReport report = ProposalReport.newProposalReport(
                    unknownUserId,
                    IncomingMessageId.of(UUID.randomUUID().toString()),
                    "conversation-1",
                    "sent-message-1");

            assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> adapter.store(report));
        }
    }

    @Nested
    @DisplayName("finding proposal reports by incoming message id")
    class FindByIncomingMessageId {

        @Test
        @DisplayName(
                "when a message has two reports and another has one - then only the first message's reports are returned")
        void whenOneMessageHasTwoReportsAndAnotherHasOne_thenOnlyFirstMessagesReportsReturned() {
            long userId = storedUserId("find-reports-two-for-one-message-user");
            IncomingMessageId targetMessage =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            IncomingMessageId otherMessage =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            Instant base = Instant.now().minusSeconds(60);
            ProposalReportRowUtils.storedReport(
                    jdbcAggregateTemplate, userId, targetMessage.value(), "conversation-1", "sent-message-1", base);
            ProposalReportRowUtils.storedReport(
                    jdbcAggregateTemplate,
                    userId,
                    targetMessage.value(),
                    "conversation-1",
                    "sent-message-2",
                    base.plusSeconds(10));
            ProposalReportRowUtils.storedReport(
                    jdbcAggregateTemplate,
                    userId,
                    otherMessage.value(),
                    "conversation-1",
                    "sent-message-3",
                    base.plusSeconds(20));

            List<ProposalReport> answer = adapter.findByIncomingMessageId(userId, targetMessage);

            assertThat(answer).hasSize(2);
            assertThat(answer)
                    .extracting(ProposalReport::sentMessageId)
                    .containsExactly("sent-message-1", "sent-message-2");
        }

        @Test
        @DisplayName("when a message no report was ever recorded for is asked about - then the answer is empty")
        void whenMessageHasNoRecordedReport_thenAnswerIsEmpty() {
            long userId = storedUserId("find-reports-no-report-user");

            List<ProposalReport> answer = adapter.findByIncomingMessageId(
                    userId, IncomingMessageId.of(UUID.randomUUID().toString()));

            assertThat(answer).isEmpty();
        }

        @Test
        @DisplayName(
                "when another person's report for the same message id exists - then it is not answered for the caller")
        void whenAnotherPersonsReportForSameMessageIdExists_thenNotAnsweredForCaller() {
            long ownerUserId = storedUserId("find-reports-owner-user");
            IncomingMessageId message = IncomingMessageId.of(UUID.randomUUID().toString());
            ProposalReportRowUtils.storedReport(
                    jdbcAggregateTemplate,
                    ownerUserId,
                    message.value(),
                    "conversation-1",
                    "sent-message-1",
                    Instant.now().minusSeconds(30));
            long callerUserId = storedUserId("find-reports-caller-user");

            List<ProposalReport> answer = adapter.findByIncomingMessageId(callerUserId, message);

            assertThat(answer).isEmpty();
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private List<ProposalReportEntity> reportRowsFor(long userId) {
        return ProposalReportRowUtils.proposalReportRowsFor(jdbcAggregateTemplate, userId);
    }
}
