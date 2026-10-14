package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.CurrencyTotal;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.ReportLocation;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.SpendingSummary;
import bot.finance.application.dto.TurnEnding;
import bot.finance.application.dto.TurnReport;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.IncomingTurn;
import bot.finance.domain.model.ProposalReport;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class IncomingTurnReportTest {

    private static final long USER_ID = 7L;
    private static final long TURN_ID = 5L;
    private static final String CONVERSATION_ID = "777";
    private static final String INBOUND_MESSAGE_ID = "1";
    private static final String TEXT = "spent 12 on coffee";
    private static final IncomingMessageId REFERENCE = IncomingMessageId.of(CONVERSATION_ID, INBOUND_MESSAGE_ID);
    private static final SpendingPeriod JULY_WEEK =
            new SpendingPeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-07"));
    private static final SpendingPeriod NEXT_WEEK =
            new SpendingPeriod(LocalDate.parse("2026-07-08"), LocalDate.parse("2026-07-14"));

    private IncomingTurnRepository incomingTurnRepository;
    private ExpenseRepository expenseRepository;
    private SpendingQueryRepository spendingQueryRepository;
    private MessageDeliveryPort messageDeliveryPort;
    private ProposalReportRepository proposalReportRepository;
    private TurnMeters turnMeters;
    private Logger log;
    private IncomingTurn turn;
    private IncomingTurnReport report;

    @BeforeEach
    void setUp() {
        incomingTurnRepository = mock(IncomingTurnRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        spendingQueryRepository = mock(SpendingQueryRepository.class);
        messageDeliveryPort = mock(MessageDeliveryPort.class);
        proposalReportRepository = mock(ProposalReportRepository.class);
        turnMeters = mock(TurnMeters.class);
        log = mock(Logger.class);
        turn = IncomingTurn.stored(
                TURN_ID,
                USER_ID,
                CONVERSATION_ID,
                INBOUND_MESSAGE_ID,
                TEXT,
                Instant.parse("2026-08-05T10:00:00Z"),
                Optional.empty(),
                Optional.empty());
        report = new IncomingTurnReport(
                incomingTurnRepository,
                expenseRepository,
                spendingQueryRepository,
                messageDeliveryPort,
                proposalReportRepository,
                turnMeters,
                log);
        when(incomingTurnRepository.delete(TURN_ID)).thenReturn(true);
        when(messageDeliveryPort.deliver(any())).thenReturn(Optional.empty());
    }

    private static ProposalSummary proposal(String category, String grouping, long minor) {
        return new ProposalSummary(
                category, grouping, "description", Optional.empty(), new Money(minor, CurrencyCode.of("USD")));
    }

    private List<ProposalSummary> givenProposals(ProposalSummary... proposals) {
        List<ProposalSummary> listed = List.of(proposals);
        when(expenseRepository.findSummariesByMessageReference(USER_ID, REFERENCE))
                .thenReturn(listed);
        return listed;
    }

    private void givenPeriods(SpendingPeriod... periods) {
        when(spendingQueryRepository.findPeriodsByMessageReference(USER_ID, REFERENCE))
                .thenReturn(List.of(periods));
    }

    private static List<CurrencyTotal> oneTotal() {
        return List.of(new CurrencyTotal(new Money(500, CurrencyCode.of("USD")), 1));
    }

    private TurnReport deliveredReport() {
        ArgumentCaptor<TurnReport> captor = ArgumentCaptor.forClass(TurnReport.class);
        verify(messageDeliveryPort).deliver(captor.capture());
        return captor.getValue();
    }

    private static String render(String message, Object[] args) {
        return message + " " + Arrays.stream(args).map(String::valueOf).collect(Collectors.joining(" "));
    }

    private String errorLine() {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(log).error(message.capture(), args.capture());
        return render(message.getValue(), args.getValue());
    }

    private String warnLine() {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(log).warn(message.capture(), args.capture());
        return render(message.getValue(), args.getValue());
    }

    private void verifyNoErrorLine() {
        verify(log, never()).error(anyString(), any(Object[].class));
        verify(log, never()).error(anyString(), any(Throwable.class));
    }

    private PersistenceFailedException persistenceFailure() {
        return new PersistenceFailedException("failed", new RuntimeException());
    }

    enum Content {
        PROPOSAL,
        PERIOD
    }

    enum ReadPoint {
        PROPOSALS,
        PERIODS,
        TOTALS
    }

    @Nested
    @DisplayName("settling a turn at its end")
    class Settle {

        @Test
        @DisplayName(
                "when two proposals are under the message - then it deletes, delivers a RECORDED report and counts")
        void whenTwoProposalsAreUnderTheMessage_thenDeletesDeliversRecordedReportAndCounts() {
            List<ProposalSummary> proposals =
                    givenProposals(proposal("Coffee", "Food", 500), proposal("Fuel", "Auto", 4000));

            boolean settled = report.settle(turn, TurnEnding.ACTED_ON);

            assertThat(settled).isTrue();
            InOrder order = inOrder(incomingTurnRepository, messageDeliveryPort);
            order.verify(incomingTurnRepository).delete(TURN_ID);
            order.verify(messageDeliveryPort).deliver(any(TurnReport.class));
            TurnReport delivered = deliveredReport();
            assertThat(delivered.outcome()).isEqualTo(ReportOutcome.RECORDED);
            assertThat(delivered.proposals()).containsExactlyElementsOf(proposals);
            assertThat(delivered.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(delivered.inboundMessageId()).isEqualTo(INBOUND_MESSAGE_ID);
            assertThat(delivered.reference()).isEqualTo(REFERENCE);
            verify(turnMeters).countTurn(ReportOutcome.RECORDED);
        }

        @Test
        @DisplayName("when nothing is under the message - then the report is NOTHING_IDENTIFIED")
        void whenNothingIsUnderTheMessage_thenTheReportIsNothingIdentified() {
            report.settle(turn, TurnEnding.ACTED_ON);

            TurnReport delivered = deliveredReport();
            assertThat(delivered.outcome()).isEqualTo(ReportOutcome.NOTHING_IDENTIFIED);
            assertThat(delivered.proposals()).isEmpty();
            assertThat(delivered.summaries()).isEmpty();
            verify(turnMeters).countTurn(ReportOutcome.NOTHING_IDENTIFIED);
        }

        @Test
        @DisplayName("when one period is under the message - then the report is ANSWERED with that period's totals")
        void whenOnePeriodIsUnderTheMessage_thenTheReportIsAnsweredWithThatPeriodsTotals() {
            givenPeriods(JULY_WEEK);
            when(expenseRepository.totalsByCurrency(USER_ID, JULY_WEEK)).thenReturn(oneTotal());

            report.settle(turn, TurnEnding.ACTED_ON);

            TurnReport delivered = deliveredReport();
            assertThat(delivered.outcome()).isEqualTo(ReportOutcome.ANSWERED);
            assertThat(delivered.summaries()).containsExactly(new SpendingSummary(JULY_WEEK, oneTotal()));
            verify(turnMeters).countTurn(ReportOutcome.ANSWERED);
        }

        @Test
        @DisplayName("when one period is under the message - then the periods are discarded after the delivery")
        void whenOnePeriodIsUnderTheMessage_thenThePeriodsAreDiscardedAfterTheDelivery() {
            givenPeriods(JULY_WEEK);
            when(expenseRepository.totalsByCurrency(USER_ID, JULY_WEEK)).thenReturn(oneTotal());

            report.settle(turn, TurnEnding.ACTED_ON);

            InOrder order = inOrder(messageDeliveryPort, spendingQueryRepository);
            order.verify(messageDeliveryPort).deliver(any(TurnReport.class));
            order.verify(spendingQueryRepository).discard(USER_ID, REFERENCE);
        }

        @Test
        @DisplayName(
                "when a proposal and a period are under the message - then the report is RECORDED and carries both")
        void whenAProposalAndAPeriodAreUnderTheMessage_thenTheReportIsRecordedAndCarriesBoth() {
            List<ProposalSummary> proposals = givenProposals(proposal("Coffee", "Food", 500));
            givenPeriods(JULY_WEEK);
            when(expenseRepository.totalsByCurrency(USER_ID, JULY_WEEK)).thenReturn(oneTotal());

            report.settle(turn, TurnEnding.ACTED_ON);

            TurnReport delivered = deliveredReport();
            assertThat(delivered.outcome()).isEqualTo(ReportOutcome.RECORDED);
            assertThat(delivered.proposals()).containsExactlyElementsOf(proposals);
            assertThat(delivered.summaries()).containsExactly(new SpendingSummary(JULY_WEEK, oneTotal()));
        }

        @Test
        @DisplayName("when two periods are under the message and one holds nothing - then each gets its own summary")
        void whenTwoPeriodsAreUnderTheMessageAndOneHoldsNothing_thenEachGetsItsOwnSummary() {
            givenPeriods(JULY_WEEK, NEXT_WEEK);
            when(expenseRepository.totalsByCurrency(USER_ID, JULY_WEEK)).thenReturn(oneTotal());
            when(expenseRepository.totalsByCurrency(USER_ID, NEXT_WEEK)).thenReturn(List.of());

            report.settle(turn, TurnEnding.ACTED_ON);

            assertThat(deliveredReport().summaries())
                    .containsExactly(
                            new SpendingSummary(JULY_WEEK, oneTotal()), new SpendingSummary(NEXT_WEEK, List.of()));
        }

        @ParameterizedTest
        @CsvSource({"UNAVAILABLE,PROPOSAL", "UNAVAILABLE,PERIOD", "FAILED,PROPOSAL", "FAILED,PERIOD"})
        @DisplayName("when the ending is not acted on and something is under the message - then the report is PARTIAL")
        void whenEndingIsNotActedOnAndSomethingIsUnderTheMessage_thenTheReportIsPartial(
                TurnEnding ending, Content content) {
            if (content == Content.PROPOSAL) {
                givenProposals(proposal("Coffee", "Food", 500));
            } else {
                givenPeriods(JULY_WEEK);
                when(expenseRepository.totalsByCurrency(USER_ID, JULY_WEEK)).thenReturn(oneTotal());
            }

            report.settle(turn, ending);

            assertThat(deliveredReport().outcome()).isEqualTo(ReportOutcome.PARTIAL);
            verify(turnMeters).countTurn(ReportOutcome.PARTIAL);
        }

        @ParameterizedTest
        @CsvSource({"UNAVAILABLE,PROPOSAL", "UNAVAILABLE,PERIOD", "FAILED,PROPOSAL", "FAILED,PERIOD"})
        @DisplayName(
                "when the ending is not acted on and something is under the message - then one error line is written")
        void whenEndingIsNotActedOnAndSomethingIsUnderTheMessage_thenOneErrorLineNamesTheMessageAndOutcome(
                TurnEnding ending, Content content) {
            if (content == Content.PROPOSAL) {
                givenProposals(proposal("Coffee", "Food", 500));
            } else {
                givenPeriods(JULY_WEEK);
                when(expenseRepository.totalsByCurrency(USER_ID, JULY_WEEK)).thenReturn(oneTotal());
            }

            report.settle(turn, ending);

            assertThat(errorLine())
                    .contains(REFERENCE.value())
                    .contains(ReportOutcome.PARTIAL.name())
                    .doesNotContain(TEXT);
        }

        @ParameterizedTest
        @EnumSource(
                value = TurnEnding.class,
                names = {"UNAVAILABLE", "FAILED"})
        @DisplayName("when the ending is not acted on and nothing is under the message - then the report is FAILED")
        void whenEndingIsNotActedOnAndNothingIsUnderTheMessage_thenTheReportIsFailed(TurnEnding ending) {
            report.settle(turn, ending);

            assertThat(deliveredReport().outcome()).isEqualTo(ReportOutcome.FAILED);
            verify(turnMeters).countTurn(ReportOutcome.FAILED);
        }

        @ParameterizedTest
        @EnumSource(
                value = TurnEnding.class,
                names = {"UNAVAILABLE", "FAILED"})
        @DisplayName(
                "when the ending is not acted on and nothing is under the message - then one error line is written")
        void whenEndingIsNotActedOnAndNothingIsUnderTheMessage_thenOneErrorLineNamesTheMessageAndOutcome(
                TurnEnding ending) {
            report.settle(turn, ending);

            assertThat(errorLine())
                    .contains(REFERENCE.value())
                    .contains(ReportOutcome.FAILED.name())
                    .doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when the ending is acted on - then no error line is written")
        void whenTheEndingIsActedOn_thenNoErrorLineIsWritten() {
            givenProposals(proposal("Coffee", "Food", 500));

            report.settle(turn, TurnEnding.ACTED_ON);

            verify(messageDeliveryPort).deliver(any(TurnReport.class));
            verifyNoErrorLine();
        }

        @ParameterizedTest
        @EnumSource(ReadPoint.class)
        @DisplayName("when a read throws PersistenceFailedException - then it propagates and nothing is deleted")
        void whenAReadThrowsPersistenceFailedException_thenItPropagatesAndNothingIsDeletedOrDelivered(
                ReadPoint readPoint) {
            PersistenceFailedException failure = persistenceFailure();
            switch (readPoint) {
                case PROPOSALS ->
                    when(expenseRepository.findSummariesByMessageReference(USER_ID, REFERENCE))
                            .thenThrow(failure);
                case PERIODS ->
                    when(spendingQueryRepository.findPeriodsByMessageReference(USER_ID, REFERENCE))
                            .thenThrow(failure);
                case TOTALS -> {
                    givenPeriods(JULY_WEEK);
                    when(expenseRepository.totalsByCurrency(USER_ID, JULY_WEEK)).thenThrow(failure);
                }
            }

            assertThatThrownBy(() -> report.settle(turn, TurnEnding.ACTED_ON)).isSameAs(failure);

            verify(incomingTurnRepository, never()).delete(anyLong());
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when the delete throws PersistenceFailedException - then it propagates and nothing is delivered")
        void whenTheDeleteThrowsPersistenceFailedException_thenItPropagatesAndNothingIsDelivered() {
            PersistenceFailedException failure = persistenceFailure();
            when(incomingTurnRepository.delete(TURN_ID)).thenThrow(failure);

            assertThatThrownBy(() -> report.settle(turn, TurnEnding.ACTED_ON)).isSameAs(failure);

            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when the delete removes nothing - then it answers false and delivers and counts nothing")
        void whenTheDeleteRemovesNothing_thenItAnswersFalseAndDeliversAndCountsNothing() {
            when(incomingTurnRepository.delete(TURN_ID)).thenReturn(false);

            boolean settled = report.settle(turn, TurnEnding.FAILED);

            assertThat(settled).isFalse();
            verify(incomingTurnRepository).delete(TURN_ID);
            verifyNoInteractions(messageDeliveryPort);
            verifyNoInteractions(turnMeters);
            verifyNoErrorLine();
        }

        @Test
        @DisplayName("when the delivery answers a location - then one report row is stored at that location")
        void whenTheDeliveryAnswersALocation_thenOneReportRowIsStoredAtThatLocation() {
            when(messageDeliveryPort.deliver(any()))
                    .thenReturn(Optional.of(new ReportLocation(CONVERSATION_ID, "999")));

            report.settle(turn, TurnEnding.ACTED_ON);

            ArgumentCaptor<ProposalReport> captor = ArgumentCaptor.forClass(ProposalReport.class);
            verify(proposalReportRepository).store(captor.capture());
            ProposalReport stored = captor.getValue();
            assertThat(stored.userId()).isEqualTo(USER_ID);
            assertThat(stored.incomingMessageId()).isEqualTo(REFERENCE);
            assertThat(stored.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(stored.sentMessageId()).isEqualTo("999");
        }

        @Test
        @DisplayName("when storing the report row throws PersistenceFailedException - then the turn is still counted")
        void whenStoringTheReportRowThrowsPersistenceFailedException_thenTheTurnIsStillCounted() {
            when(messageDeliveryPort.deliver(any()))
                    .thenReturn(Optional.of(new ReportLocation(CONVERSATION_ID, "999")));
            when(proposalReportRepository.store(any())).thenThrow(persistenceFailure());

            assertThatCode(() -> report.settle(turn, TurnEnding.ACTED_ON)).doesNotThrowAnyException();

            verify(turnMeters).countTurn(ReportOutcome.NOTHING_IDENTIFIED);
        }

        @Test
        @DisplayName("when the delivery throws MessageDeliveryFailedException - then the turn is counted unreported")
        void whenTheDeliveryThrowsMessageDeliveryFailedException_thenTheTurnIsCountedUnreported() {
            givenPeriods(JULY_WEEK);
            when(expenseRepository.totalsByCurrency(USER_ID, JULY_WEEK)).thenReturn(oneTotal());
            when(messageDeliveryPort.deliver(any()))
                    .thenThrow(new MessageDeliveryFailedException("delivery failed", new RuntimeException()));

            boolean settled = report.settle(turn, TurnEnding.ACTED_ON);

            assertThat(settled).isTrue();
            verify(turnMeters).countUnreported();
            verify(turnMeters, never()).countTurn(any());
            verify(spendingQueryRepository, never()).discard(anyLong(), any());
            verifyNoInteractions(proposalReportRepository);
        }

        @Test
        @DisplayName(
                "when discarding the periods throws PersistenceFailedException - then one warning names the message")
        void whenDiscardingThePeriodsThrowsPersistenceFailedException_thenOneWarningNamesTheMessage() {
            givenPeriods(JULY_WEEK);
            when(expenseRepository.totalsByCurrency(USER_ID, JULY_WEEK)).thenReturn(oneTotal());
            when(spendingQueryRepository.discard(anyLong(), any())).thenThrow(persistenceFailure());

            assertThatCode(() -> report.settle(turn, TurnEnding.ACTED_ON)).doesNotThrowAnyException();

            assertThat(warnLine()).contains(REFERENCE.value());
        }
    }

    @Nested
    @DisplayName("settling a turn the sweep gives up on")
    class SettleSwept {

        @Test
        @DisplayName("when a proposal is under the message - then its PARTIAL report is delivered and counted")
        void whenAProposalIsUnderTheMessage_thenItsPartialReportIsDeliveredAndCounted() {
            givenProposals(proposal("Coffee", "Food", 500));

            boolean settled = report.settleSwept(turn);

            assertThat(settled).isTrue();
            InOrder order = inOrder(incomingTurnRepository, messageDeliveryPort);
            order.verify(incomingTurnRepository).delete(TURN_ID);
            order.verify(messageDeliveryPort).deliver(any(TurnReport.class));
            assertThat(deliveredReport().outcome()).isEqualTo(ReportOutcome.PARTIAL);
            verify(turnMeters).countTurn(ReportOutcome.PARTIAL);
        }

        @Test
        @DisplayName("when a proposal is under the message - then no error line is written")
        void whenAProposalIsUnderTheMessage_thenNoErrorLineIsWritten() {
            givenProposals(proposal("Coffee", "Food", 500));

            report.settleSwept(turn);

            verify(messageDeliveryPort).deliver(any(TurnReport.class));
            verifyNoErrorLine();
        }

        @Test
        @DisplayName("when nothing is under the message - then its FAILED report is delivered and counted")
        void whenNothingIsUnderTheMessage_thenItsFailedReportIsDeliveredAndCounted() {
            report.settleSwept(turn);

            assertThat(deliveredReport().outcome()).isEqualTo(ReportOutcome.FAILED);
            verify(turnMeters).countTurn(ReportOutcome.FAILED);
        }
    }
}
