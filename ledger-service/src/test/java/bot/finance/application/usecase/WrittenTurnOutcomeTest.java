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

import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.TurnEnding;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.SpendingPeriod;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class WrittenTurnOutcomeTest {

    private static final long USER_ID = 7L;
    private static final long MESSAGE_ID = 42L;
    private static final String TEXT = "coffee 3.40, taxi home 14";
    private static final Instant NOW = Instant.parse("2026-08-05T10:15:30Z");
    private static final IncomingMessageId REFERENCE = IncomingMessageId.of("web:42");
    private static final LocalDate DAY = LocalDate.parse("2026-08-05");
    private static final SpendingPeriod PERIOD =
            new SpendingPeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-07"));

    private WrittenMessageRepository writtenMessageRepository;
    private ExpenseRepository expenseRepository;
    private SpendingQueryRepository spendingQueryRepository;
    private TurnMeters turnMeters;
    private Logger log;
    private WrittenMessageView message;
    private WrittenTurnOutcome outcome;

    @BeforeEach
    void setUp() {
        writtenMessageRepository = mock(WrittenMessageRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        spendingQueryRepository = mock(SpendingQueryRepository.class);
        turnMeters = mock(TurnMeters.class);
        log = mock(Logger.class);
        message = new WrittenMessageView(
                MESSAGE_ID, USER_ID, TEXT, WrittenMessageStatus.READING, Optional.empty(), NOW.minusSeconds(60));
        outcome = new WrittenTurnOutcome(
                writtenMessageRepository,
                expenseRepository,
                spendingQueryRepository,
                turnMeters,
                Clock.fixed(NOW, ZoneOffset.UTC),
                log);
        when(writtenMessageRepository.settle(anyLong(), any(), any())).thenReturn(true);
    }

    private void givenADay() {
        when(expenseRepository.findDaysByMessageReference(USER_ID, REFERENCE)).thenReturn(List.of(DAY));
    }

    private void givenAPeriod() {
        when(spendingQueryRepository.findPeriodsByMessageReference(USER_ID, REFERENCE))
                .thenReturn(List.of(PERIOD));
    }

    private void verifySettledAs(ReportOutcome expected) {
        verify(writtenMessageRepository).settle(MESSAGE_ID, expected, NOW);
    }

    private static String render(String text, Object[] args) {
        return text + " " + Arrays.stream(args).map(String::valueOf).collect(Collectors.joining(" "));
    }

    private String errorLine() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(log).error(text.capture(), args.capture());
        return render(text.getValue(), args.getValue());
    }

    private String warnLine() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(log).warn(text.capture(), args.capture());
        return render(text.getValue(), args.getValue());
    }

    private PersistenceFailedException persistenceFailure() {
        return new PersistenceFailedException("failed", new RuntimeException());
    }

    enum Rest {
        PERIOD_ONLY,
        NOTHING
    }

    enum ReadPoint {
        DAYS,
        PERIODS
    }

    @Nested
    @DisplayName("settling a written message at its end")
    class Settle {

        @Test
        @DisplayName(
                "when a day is stored and no period - then the text is settled RECORDED, counted, and answers true")
        void whenADayIsStoredAndNoPeriod_thenTheTextIsSettledRecordedCountedAndAnswersTrue() {
            givenADay();

            boolean settled = outcome.settle(message, TurnEnding.ACTED_ON);

            assertThat(settled).isTrue();
            verifySettledAs(ReportOutcome.RECORDED);
            verify(turnMeters).countTurn(ReportOutcome.RECORDED);
        }

        @Test
        @DisplayName("when no day and no period is stored - then the text is settled NOTHING_IDENTIFIED")
        void whenNoDayAndNoPeriodIsStored_thenTheTextIsSettledNothingIdentified() {
            outcome.settle(message, TurnEnding.ACTED_ON);

            verifySettledAs(ReportOutcome.NOTHING_IDENTIFIED);
        }

        @Test
        @DisplayName("when no day and one period is stored - then the text is settled ANSWERED")
        void whenNoDayAndOnePeriodIsStored_thenTheTextIsSettledAnswered() {
            givenAPeriod();

            outcome.settle(message, TurnEnding.ACTED_ON);

            verifySettledAs(ReportOutcome.ANSWERED);
        }

        @Test
        @DisplayName("when no day and one period is stored - then the periods are discarded after the settle")
        void whenNoDayAndOnePeriodIsStored_thenThePeriodsAreDiscardedAfterTheSettle() {
            givenAPeriod();

            outcome.settle(message, TurnEnding.ACTED_ON);

            InOrder order = inOrder(writtenMessageRepository, spendingQueryRepository);
            order.verify(writtenMessageRepository).settle(MESSAGE_ID, ReportOutcome.ANSWERED, NOW);
            order.verify(spendingQueryRepository).discard(USER_ID, REFERENCE);
        }

        @ParameterizedTest
        @EnumSource(
                value = TurnEnding.class,
                names = {"UNAVAILABLE", "FAILED"})
        @DisplayName("when the ending is not acted on and a day is stored - then the text is settled PARTIAL")
        void whenTheEndingIsNotActedOnAndADayIsStored_thenTheTextIsSettledPartial(TurnEnding ending) {
            givenADay();

            outcome.settle(message, ending);

            verifySettledAs(ReportOutcome.PARTIAL);
            verify(turnMeters).countTurn(ReportOutcome.PARTIAL);
        }

        @ParameterizedTest
        @EnumSource(
                value = TurnEnding.class,
                names = {"UNAVAILABLE", "FAILED"})
        @DisplayName("when the ending is not acted on and a day is stored - then one error line is written")
        void whenTheEndingIsNotActedOnAndADayIsStored_thenOneErrorLineNamesTheMessageAndOutcome(TurnEnding ending) {
            givenADay();

            outcome.settle(message, ending);

            assertThat(errorLine())
                    .contains(String.valueOf(MESSAGE_ID))
                    .contains(ReportOutcome.PARTIAL.name())
                    .doesNotContain(TEXT);
        }

        @ParameterizedTest
        @CsvSource({"UNAVAILABLE,PERIOD_ONLY", "UNAVAILABLE,NOTHING", "FAILED,PERIOD_ONLY", "FAILED,NOTHING"})
        @DisplayName("when the ending is not acted on and no day is stored - then the text is settled FAILED")
        void whenTheEndingIsNotActedOnAndNoDayIsStored_thenTheTextIsSettledFailed(TurnEnding ending, Rest rest) {
            if (rest == Rest.PERIOD_ONLY) {
                givenAPeriod();
            }

            outcome.settle(message, ending);

            verifySettledAs(ReportOutcome.FAILED);
            verify(turnMeters).countTurn(ReportOutcome.FAILED);
        }

        @ParameterizedTest
        @CsvSource({"UNAVAILABLE,PERIOD_ONLY", "UNAVAILABLE,NOTHING", "FAILED,PERIOD_ONLY", "FAILED,NOTHING"})
        @DisplayName("when the ending is not acted on and no day is stored - then one error line is written")
        void whenTheEndingIsNotActedOnAndNoDayIsStored_thenOneErrorLineNamesTheMessageAndOutcome(
                TurnEnding ending, Rest rest) {
            if (rest == Rest.PERIOD_ONLY) {
                givenAPeriod();
            }

            outcome.settle(message, ending);

            assertThat(errorLine())
                    .contains(String.valueOf(MESSAGE_ID))
                    .contains(ReportOutcome.FAILED.name())
                    .doesNotContain(TEXT);
        }

        @ParameterizedTest
        @EnumSource(ReadPoint.class)
        @DisplayName("when a read throws PersistenceFailedException - then it propagates and the text is never settled")
        void whenAReadThrowsPersistenceFailedException_thenItPropagatesAndTheTextIsNeverSettled(ReadPoint readPoint) {
            PersistenceFailedException failure = persistenceFailure();
            switch (readPoint) {
                case DAYS ->
                    when(expenseRepository.findDaysByMessageReference(USER_ID, REFERENCE))
                            .thenThrow(failure);
                case PERIODS ->
                    when(spendingQueryRepository.findPeriodsByMessageReference(USER_ID, REFERENCE))
                            .thenThrow(failure);
            }

            assertThatThrownBy(() -> outcome.settle(message, TurnEnding.ACTED_ON))
                    .isSameAs(failure);

            verify(writtenMessageRepository, never()).settle(anyLong(), any(), any());
        }

        @Test
        @DisplayName("when the settle throws PersistenceFailedException - then it propagates and nothing is counted")
        void whenTheSettleThrowsPersistenceFailedException_thenItPropagatesAndNothingIsCounted() {
            PersistenceFailedException failure = persistenceFailure();
            when(writtenMessageRepository.settle(anyLong(), any(), any())).thenThrow(failure);

            assertThatThrownBy(() -> outcome.settle(message, TurnEnding.ACTED_ON))
                    .isSameAs(failure);

            verifyNoInteractions(turnMeters);
        }

        @Test
        @DisplayName("when the settle answers false - then it answers false and counts and discards nothing")
        void whenTheSettleAnswersFalse_thenItAnswersFalseAndCountsAndDiscardsNothing() {
            givenAPeriod();
            when(writtenMessageRepository.settle(anyLong(), any(), any())).thenReturn(false);

            boolean settled = outcome.settle(message, TurnEnding.FAILED);

            assertThat(settled).isFalse();
            verifySettledAs(ReportOutcome.FAILED);
            verifyNoInteractions(turnMeters);
            verify(spendingQueryRepository, never()).discard(anyLong(), any());
            verify(log, never()).error(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("when discarding the periods throws PersistenceFailedException - then the turn is still counted")
        void whenDiscardingThePeriodsThrowsPersistenceFailedException_thenTheTurnIsStillCounted() {
            givenAPeriod();
            when(spendingQueryRepository.discard(anyLong(), any())).thenThrow(persistenceFailure());

            assertThatCode(() -> outcome.settle(message, TurnEnding.ACTED_ON)).doesNotThrowAnyException();

            verify(turnMeters).countTurn(ReportOutcome.ANSWERED);
        }

        @Test
        @DisplayName(
                "when discarding the periods throws PersistenceFailedException - then one warning names the message")
        void whenDiscardingThePeriodsThrowsPersistenceFailedException_thenOneWarningNamesTheMessage() {
            givenAPeriod();
            when(spendingQueryRepository.discard(anyLong(), any())).thenThrow(persistenceFailure());

            outcome.settle(message, TurnEnding.ACTED_ON);

            assertThat(warnLine()).contains(REFERENCE.value());
        }
    }
}
