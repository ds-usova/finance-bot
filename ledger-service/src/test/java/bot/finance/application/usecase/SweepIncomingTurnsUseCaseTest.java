package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.TurnChannel;
import bot.finance.application.dto.TurnReport;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.IncomingTurn;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.Money;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class SweepIncomingTurnsUseCaseTest {

    private static final long USER_ID = 31L;
    private static final String TEXT = "spent 12 on coffee";
    private static final Instant NOW = Instant.parse("2026-08-05T10:15:30Z");
    private static final Duration LONGEST_TURN_LIFE = Duration.ofMinutes(3).plusSeconds(30);
    private static final Duration MAX_QUEUE_WAIT = Duration.ofMinutes(10);

    private IncomingTurnRepository incomingTurnRepository;
    private ExpenseRepository expenseRepository;
    private SpendingQueryRepository spendingQueryRepository;
    private MessageDeliveryPort messageDeliveryPort;
    private ProposalReportRepository proposalReportRepository;
    private TurnMeters turnMeters;
    private Logger log;
    private SweepIncomingTurnsUseCase useCase;

    @BeforeEach
    void setUp() {
        incomingTurnRepository = mock(IncomingTurnRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        spendingQueryRepository = mock(SpendingQueryRepository.class);
        messageDeliveryPort = mock(MessageDeliveryPort.class);
        proposalReportRepository = mock(ProposalReportRepository.class);
        turnMeters = mock(TurnMeters.class);
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(SweepIncomingTurnsUseCase.class)).thenReturn(log);
        useCase = new SweepIncomingTurnsUseCase(
                incomingTurnRepository,
                expenseRepository,
                spendingQueryRepository,
                messageDeliveryPort,
                proposalReportRepository,
                turnMeters,
                Clock.fixed(NOW, ZoneOffset.UTC),
                LONGEST_TURN_LIFE,
                MAX_QUEUE_WAIT,
                loggerFactory);
        when(incomingTurnRepository.findHandedOverBefore(any())).thenReturn(List.of());
        when(incomingTurnRepository.findQueuedForSweep(any(), any())).thenReturn(List.of());
        when(incomingTurnRepository.oldestQueuedKeptAt()).thenReturn(Optional.empty());
        when(incomingTurnRepository.delete(anyLong())).thenReturn(true);
        when(messageDeliveryPort.deliver(any())).thenReturn(Optional.empty());
    }

    private static IncomingTurn turn(long id, String inboundMessageId) {
        return IncomingTurn.stored(
                id,
                USER_ID,
                "777",
                inboundMessageId,
                TEXT,
                NOW.minus(Duration.ofMinutes(30)),
                Optional.of(NOW.minus(Duration.ofMinutes(20))),
                Optional.of(NOW.minus(Duration.ofMinutes(19))));
    }

    private void givenHandedOver(IncomingTurn... turns) {
        when(incomingTurnRepository.findHandedOverBefore(NOW.minus(LONGEST_TURN_LIFE)))
                .thenReturn(List.of(turns));
    }

    private void givenProposalUnder(IncomingTurn turn) {
        when(expenseRepository.findSummariesByMessageReference(USER_ID, turn.reference()))
                .thenReturn(List.of(new ProposalSummary(
                        "Coffee", "Food", "espresso", Optional.empty(), new Money(500, CurrencyCode.of("USD")))));
    }

    private TurnReport deliveredReport() {
        ArgumentCaptor<TurnReport> captor = ArgumentCaptor.forClass(TurnReport.class);
        verify(messageDeliveryPort).deliver(captor.capture());
        return captor.getValue();
    }

    private PersistenceFailedException persistenceFailure() {
        return new PersistenceFailedException("failed", new RuntimeException());
    }

    enum Failing {
        READ,
        DELETE
    }

    enum Finding {
        HANDED_OVER,
        QUEUED
    }

    @Nested
    @DisplayName("sweeping incoming turns")
    class Sweep {

        @Test
        @DisplayName("when no turn is handed over or queued - then it answers 0 and the oldest age is recorded as zero")
        void whenNoTurnIsHandedOverOrQueued_thenItAnswersZeroAndTheOldestAgeIsRecordedAsZero() {
            int swept = useCase.sweep();

            assertThat(swept).isZero();
            verify(turnMeters).recordOldestQueued(TurnChannel.INCOMING, Duration.ZERO);
            verify(incomingTurnRepository).findHandedOverBefore(NOW.minus(LONGEST_TURN_LIFE));
            verify(incomingTurnRepository, never()).delete(anyLong());
        }

        @Test
        @DisplayName(
                "when a turn is handed over past the longest life - then it is deleted and its PARTIAL report delivered")
        void whenATurnIsHandedOverPastTheLongestLife_thenItIsDeletedAndItsPartialReportDelivered() {
            IncomingTurn handedOver = turn(5L, "1");
            givenHandedOver(handedOver);
            givenProposalUnder(handedOver);

            int swept = useCase.sweep();

            assertThat(swept).isEqualTo(1);
            verify(incomingTurnRepository).delete(5L);
            assertThat(deliveredReport().outcome()).isEqualTo(ReportOutcome.PARTIAL);
            verify(turnMeters).countTurn(ReportOutcome.PARTIAL);
        }

        @Test
        @DisplayName(
                "when a turn is handed over past the longest life - then it is counted swept for the incoming channel")
        void whenATurnIsHandedOverPastTheLongestLife_thenItIsCountedSweptForTheIncomingChannel() {
            givenHandedOver(turn(5L, "1"));

            useCase.sweep();

            verify(turnMeters).countSwept(TurnChannel.INCOMING);
        }

        @Test
        @DisplayName(
                "when a turn is swept - then one warning names the message and the person and no error line is written")
        void whenATurnIsSwept_thenOneWarningNamesTheMessageAndThePersonAndNoErrorLineIsWritten() {
            IncomingTurn handedOver = turn(5L, "1");
            givenHandedOver(handedOver);
            givenProposalUnder(handedOver);

            useCase.sweep();

            ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
            verify(log).warn(message.capture(), args.capture());
            String line = message.getValue() + " "
                    + Arrays.stream(args.getValue()).map(String::valueOf).collect(Collectors.joining(" "));
            assertThat(line).contains("777:1").contains(String.valueOf(USER_ID)).doesNotContain(TEXT);
            verify(log, never()).error(anyString(), any(Object[].class));
            verify(log, never()).error(anyString(), any(Throwable.class));
        }

        @Test
        @DisplayName(
                "when a turn queued past the wait has no recent attempt - then it gets its FAILED report and is swept")
        void whenAQueuedTurnHasNoRecentAttempt_thenItGetsItsFailedReportAndIsSwept() {
            IncomingTurn queued = turn(6L, "2");
            when(incomingTurnRepository.findQueuedForSweep(NOW.minus(MAX_QUEUE_WAIT), NOW.minus(LONGEST_TURN_LIFE)))
                    .thenReturn(List.of(queued));

            int swept = useCase.sweep();

            assertThat(swept).isEqualTo(1);
            assertThat(deliveredReport().outcome()).isEqualTo(ReportOutcome.FAILED);
            verify(turnMeters).countTurn(ReportOutcome.FAILED);
            verify(turnMeters).countSwept(TurnChannel.INCOMING);
        }

        @Test
        @DisplayName("when the report cannot be delivered - then the turn is counted unreported and swept")
        void whenTheReportCannotBeDelivered_thenTheTurnIsCountedUnreportedAndSwept() {
            givenHandedOver(turn(5L, "1"));
            when(messageDeliveryPort.deliver(any()))
                    .thenThrow(new MessageDeliveryFailedException("delivery failed", new RuntimeException()));

            assertThatCode(() -> useCase.sweep()).doesNotThrowAnyException();

            verify(turnMeters).countUnreported();
            verify(turnMeters).countSwept(TurnChannel.INCOMING);
        }

        @Test
        @DisplayName(
                "when the oldest queued turn was kept 90 seconds ago - then that age is recorded for the incoming channel")
        void whenTheOldestQueuedTurnWasKept90SecondsAgo_thenThatAgeIsRecordedForTheIncomingChannel() {
            when(incomingTurnRepository.oldestQueuedKeptAt()).thenReturn(Optional.of(NOW.minusSeconds(90)));

            useCase.sweep();

            verify(turnMeters).recordOldestQueued(TurnChannel.INCOMING, Duration.ofSeconds(90));
        }

        @ParameterizedTest
        @EnumSource(Finding.class)
        @DisplayName("when finding the turns throws PersistenceFailedException - then it propagates")
        void whenFindingTheTurnsThrowsPersistenceFailedException_thenItPropagates(Finding finding) {
            PersistenceFailedException failure = persistenceFailure();
            switch (finding) {
                case HANDED_OVER ->
                    when(incomingTurnRepository.findHandedOverBefore(any())).thenThrow(failure);
                case QUEUED ->
                    when(incomingTurnRepository.findQueuedForSweep(any(), any()))
                            .thenThrow(failure);
            }

            assertThatThrownBy(() -> useCase.sweep()).isSameAs(failure);
        }

        @ParameterizedTest
        @EnumSource(Failing.class)
        @DisplayName(
                "when settling the first of two turns throws PersistenceFailedException - then the second is not deleted")
        void whenSettlingTheFirstOfTwoTurnsThrowsPersistenceFailedException_thenTheSecondIsNotDeleted(Failing failing) {
            IncomingTurn first = turn(5L, "1");
            IncomingTurn second = turn(6L, "2");
            givenHandedOver(first, second);
            PersistenceFailedException failure = persistenceFailure();
            switch (failing) {
                case READ ->
                    when(expenseRepository.findSummariesByMessageReference(USER_ID, IncomingMessageId.of("777:1")))
                            .thenThrow(failure);
                case DELETE -> when(incomingTurnRepository.delete(5L)).thenThrow(failure);
            }

            assertThatThrownBy(() -> useCase.sweep()).isSameAs(failure);

            verify(incomingTurnRepository, never()).delete(6L);
        }
    }
}
