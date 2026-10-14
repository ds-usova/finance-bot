package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.TurnMeters;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SweepWrittenMessagesUseCaseTest {

    private static final long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-08-05T10:15:30Z");
    private static final Duration LONGEST_TURN_LIFE = Duration.ofMinutes(3).plusSeconds(30);
    private static final Duration MAX_QUEUE_WAIT = Duration.ofMinutes(10);
    private static final LocalDate DAY = LocalDate.parse("2026-08-05");

    private WrittenMessageRepository writtenMessageRepository;
    private ExpenseRepository expenseRepository;
    private SweepWrittenMessagesUseCase useCase;

    @BeforeEach
    void setUp() {
        writtenMessageRepository = mock(WrittenMessageRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(SweepWrittenMessagesUseCase.class)).thenReturn(mock(Logger.class));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        useCase = new SweepWrittenMessagesUseCase(
                writtenMessageRepository,
                expenseRepository,
                mock(TurnMeters.class),
                clock,
                LONGEST_TURN_LIFE,
                MAX_QUEUE_WAIT,
                loggerFactory);
        when(writtenMessageRepository.findReadingUpdatedBefore(any())).thenReturn(List.of());
        when(writtenMessageRepository.settle(anyLong(), any(), any())).thenReturn(true);
        when(expenseRepository.findDaysByMessageReference(anyLong(), any())).thenReturn(List.of());
    }

    private WrittenMessageView staleMessage(long id) {
        return new WrittenMessageView(id, USER_ID, "coffee 3.40", WrittenMessageStatus.READING, Optional.empty(), NOW);
    }

    private void givenStale(WrittenMessageView... views) {
        when(writtenMessageRepository.findReadingUpdatedBefore(NOW.minus(LONGEST_TURN_LIFE)))
                .thenReturn(List.of(views));
    }

    private void givenDayUnder(long messageId) {
        when(expenseRepository.findDaysByMessageReference(USER_ID, IncomingMessageId.of("web:" + messageId)))
                .thenReturn(List.of(DAY));
    }

    @Nested
    @DisplayName("sweeping stale written messages")
    class Sweep {

        @Test
        @DisplayName("when no message is stale - then it answers 0 and settles nothing")
        void whenNoMessageIsStale_thenAnswersZeroAndSettlesNothing() {
            int swept = useCase.sweep();

            assertThat(swept).isZero();
            verify(writtenMessageRepository).findReadingUpdatedBefore(NOW.minus(LONGEST_TURN_LIFE));
            verify(writtenMessageRepository, never()).settle(anyLong(), any(), any());
        }

        @Test
        @DisplayName("when a stale message has a day under it - then it is settled PARTIAL and the sweep answers 1")
        void whenStaleMessageHasADay_thenItIsSettledPartialAndTheSweepAnswersOne() {
            givenStale(staleMessage(42L));
            givenDayUnder(42L);

            int swept = useCase.sweep();

            assertThat(swept).isEqualTo(1);
            verify(writtenMessageRepository).settle(42L, ReportOutcome.PARTIAL, NOW);
        }

        @Test
        @DisplayName("when a stale message has nothing under it - then it is settled FAILED")
        void whenStaleMessageHasNothingUnderIt_thenItIsSettledFailed() {
            givenStale(staleMessage(42L));

            useCase.sweep();

            verify(writtenMessageRepository).settle(42L, ReportOutcome.FAILED, NOW);
        }

        @Test
        @DisplayName("when two messages are stale - then each is settled by what is under its own id")
        void whenTwoMessagesAreStale_thenEachIsSettledByWhatIsUnderItsOwnId() {
            givenStale(staleMessage(42L), staleMessage(43L));
            givenDayUnder(42L);

            int swept = useCase.sweep();

            assertThat(swept).isEqualTo(2);
            verify(writtenMessageRepository).settle(42L, ReportOutcome.PARTIAL, NOW);
            verify(writtenMessageRepository).settle(43L, ReportOutcome.FAILED, NOW);
        }

        @Test
        @DisplayName("when the settle answers false - then the message is not counted as swept")
        void whenSettleAnswersFalse_thenTheMessageIsNotCountedAsSwept() {
            givenStale(staleMessage(42L));
            when(writtenMessageRepository.settle(anyLong(), any(), any())).thenReturn(false);

            int swept = useCase.sweep();

            assertThat(swept).isZero();
            verify(writtenMessageRepository).settle(anyLong(), any(), any());
        }

        @Test
        @DisplayName("when reading the stale messages throws PersistenceFailedException - then it reaches the caller")
        void whenReadingTheStaleMessagesThrows_thenItReachesTheCaller() {
            PersistenceFailedException failure = new PersistenceFailedException("read failed", new RuntimeException());
            when(writtenMessageRepository.findReadingUpdatedBefore(any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.sweep()).isSameAs(failure);
        }

        @Test
        @DisplayName("when a settle throws PersistenceFailedException - then it reaches the caller")
        void whenASettleThrows_thenItReachesTheCaller() {
            givenStale(staleMessage(42L));
            PersistenceFailedException failure = new PersistenceFailedException("write failed", new RuntimeException());
            when(writtenMessageRepository.settle(anyLong(), any(), any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.sweep()).isSameAs(failure);
        }
    }
}
