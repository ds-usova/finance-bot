package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.SpendingPeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TurnReportTest {

    private static final ProposalSummary PROPOSAL = new ProposalSummary(
            "groceries", "essentials", "lunch", Optional.of("Trader Joe's"), new Money(1000, new CurrencyCode("USD")));
    private static final SpendingSummary SUMMARY =
            new SpendingSummary(new SpendingPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5)), List.of());

    @Nested
    @DisplayName("constructing a turn report")
    class TurnReportConstructor {

        @Test
        @DisplayName("when a conversation id, inbound message id, outcome, proposal list, summary list and "
                + "reference are present - then every component reads back unchanged and both lists are unmodifiable")
        void whenEveryComponentIsPresent_thenEveryComponentReadsBackUnchangedAndBothListsAreUnmodifiable() {
            MessageReference reference = MessageReference.newReference();

            TurnReport report = new TurnReport(
                    "conversation-1",
                    "message-1",
                    ReportOutcome.RECORDED,
                    List.of(PROPOSAL),
                    List.of(SUMMARY),
                    reference);

            assertThat(report.conversationId()).isEqualTo("conversation-1");
            assertThat(report.inboundMessageId()).isEqualTo("message-1");
            assertThat(report.outcome()).isEqualTo(ReportOutcome.RECORDED);
            assertThat(report.proposals()).containsExactly(PROPOSAL);
            assertThat(report.summaries()).containsExactly(SUMMARY);
            assertThat(report.reference()).isEqualTo(reference);
            assertThatThrownBy(() -> report.proposals().add(PROPOSAL))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> report.summaries().add(SUMMARY)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("when the proposals list is null - then proposals() reads back as an empty list")
        void whenProposalsListIsNull_thenProposalsReadsBackAsEmptyList() {
            TurnReport report = new TurnReport(
                    "conversation-1",
                    "message-1",
                    ReportOutcome.NOTHING_IDENTIFIED,
                    null,
                    List.of(SUMMARY),
                    MessageReference.newReference());

            assertThat(report.proposals()).isEmpty();
        }

        @Test
        @DisplayName("when the summaries list is null - then summaries() reads back as an empty list")
        void whenSummariesListIsNull_thenSummariesReadsBackAsEmptyList() {
            TurnReport report = new TurnReport(
                    "conversation-1",
                    "message-1",
                    ReportOutcome.ANSWERED,
                    List.of(PROPOSAL),
                    null,
                    MessageReference.newReference());

            assertThat(report.summaries()).isEmpty();
        }

        @Test
        @DisplayName("when mutable proposal and summary lists handed to the constructor are modified afterwards - "
                + "then proposals() and summaries() are unchanged")
        void whenMutableProposalAndSummaryListsAreModifiedAfterConstruction_thenProposalsAndSummariesAreUnchanged() {
            List<ProposalSummary> mutableProposals = new ArrayList<>(List.of(PROPOSAL));
            List<SpendingSummary> mutableSummaries = new ArrayList<>(List.of(SUMMARY));

            TurnReport report = new TurnReport(
                    "conversation-1",
                    "message-1",
                    ReportOutcome.PARTIAL,
                    mutableProposals,
                    mutableSummaries,
                    MessageReference.newReference());
            mutableProposals.add(PROPOSAL);
            mutableSummaries.add(SUMMARY);

            assertThat(report.proposals()).containsExactly(PROPOSAL);
            assertThat(report.summaries()).containsExactly(SUMMARY);
        }
    }
}
