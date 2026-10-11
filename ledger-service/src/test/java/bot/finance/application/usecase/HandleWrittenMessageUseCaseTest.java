package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.application.port.UserPreferenceRepository;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.Grouping;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.SpendingPeriod;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HandleWrittenMessageUseCaseTest {

    private static final long USER_ID = 7L;
    private static final long MESSAGE_ID = 42L;
    private static final String TEXT = "coffee 3.40, taxi home 14";
    private static final Instant NOW = Instant.parse("2026-08-05T10:15:30Z");
    private static final IncomingMessageId REFERENCE = IncomingMessageId.of("web:42");
    private static final LocalDate DAY = LocalDate.parse("2026-08-05");

    private WrittenMessageRepository writtenMessageRepository;
    private GroupingRepository groupingRepository;
    private UserPreferenceRepository userPreferenceRepository;
    private IntentExtractionPort intentExtractionPort;
    private ExpenseRepository expenseRepository;
    private SpendingQueryRepository spendingQueryRepository;
    private TurnMeters turnMeters;
    private Clock clock;
    private List<String> groupings;
    private HandleWrittenMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        writtenMessageRepository = mock(WrittenMessageRepository.class);
        groupingRepository = mock(GroupingRepository.class);
        userPreferenceRepository = mock(UserPreferenceRepository.class);
        intentExtractionPort = mock(IntentExtractionPort.class);
        expenseRepository = mock(ExpenseRepository.class);
        spendingQueryRepository = mock(SpendingQueryRepository.class);
        turnMeters = mock(TurnMeters.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(HandleWrittenMessageUseCase.class)).thenReturn(mock(Logger.class));
        useCase = new HandleWrittenMessageUseCase(
                writtenMessageRepository,
                groupingRepository,
                userPreferenceRepository,
                intentExtractionPort,
                expenseRepository,
                spendingQueryRepository,
                turnMeters,
                clock,
                loggerFactory);

        groupings = List.of("Food", "Auto", Grouping.catchAllName());
        WrittenMessageView claimed =
                new WrittenMessageView(MESSAGE_ID, USER_ID, TEXT, WrittenMessageStatus.READING, Optional.empty());
        when(writtenMessageRepository.claimOldestWaiting(NOW)).thenReturn(Optional.of(claimed));
        when(groupingRepository.findNamesWithCategories(USER_ID)).thenReturn(groupings);
        when(userPreferenceRepository.findDefaultCurrency(USER_ID)).thenReturn(Optional.empty());
        when(writtenMessageRepository.stampHandOver(MESSAGE_ID, NOW)).thenReturn(true);
        when(writtenMessageRepository.settle(anyLong(), any(), any())).thenReturn(true);
        when(expenseRepository.findDaysByMessageReference(USER_ID, REFERENCE)).thenReturn(List.of());
        when(spendingQueryRepository.findPeriodsByMessageReference(USER_ID, REFERENCE))
                .thenReturn(List.of());
    }

    private void givenOneDay() {
        when(expenseRepository.findDaysByMessageReference(USER_ID, REFERENCE)).thenReturn(List.of(DAY));
    }

    private void givenOnePeriod() {
        SpendingPeriod period = new SpendingPeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-07"));
        when(spendingQueryRepository.findPeriodsByMessageReference(USER_ID, REFERENCE))
                .thenReturn(List.of(period));
    }

    private void givenExtractionFails() {
        doThrow(new IntentExtractionFailedException("turn failed", new RuntimeException()))
                .when(intentExtractionPort)
                .extract(any());
    }

    private PersistenceFailedException persistenceFailure() {
        return new PersistenceFailedException("failed", new RuntimeException());
    }

    private void verifySettledAs(ReportOutcome outcome) {
        verify(writtenMessageRepository).settle(MESSAGE_ID, outcome, NOW);
    }

    @Nested
    @DisplayName("handling the next written message")
    class HandleNext {

        @Test
        @DisplayName("when nothing waits - then it answers false and asks no other port")
        void whenNothingWaits_thenAnswersFalseAndAsksNoOtherPort() {
            when(writtenMessageRepository.claimOldestWaiting(NOW)).thenReturn(Optional.empty());

            boolean handled = useCase.handleNext();

            assertThat(handled).isFalse();
            verify(writtenMessageRepository).claimOldestWaiting(NOW);
            verify(writtenMessageRepository, never()).stampHandOver(anyLong(), any());
            verify(writtenMessageRepository, never()).settle(anyLong(), any(), any());
            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(userPreferenceRepository);
            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(expenseRepository);
            verifyNoInteractions(spendingQueryRepository);
            verifyNoInteractions(turnMeters);
        }

        @Test
        @DisplayName("when a message is claimed and a day is stored - then the extraction request carries the turn")
        void whenMessageIsClaimedAndADayIsStored_thenTheExtractionRequestCarriesTheTurn() {
            givenOneDay();

            boolean handled = useCase.handleNext();

            assertThat(handled).isTrue();
            ArgumentCaptor<IntentExtractionRequest> captor = ArgumentCaptor.forClass(IntentExtractionRequest.class);
            verify(intentExtractionPort).extract(captor.capture());
            IntentExtractionRequest request = captor.getValue();
            assertThat(request.text()).isEqualTo(TEXT);
            assertThat(request.categoryGroupings()).isEqualTo(groupings);
            assertThat(request.catchAllGrouping()).isEqualTo(Grouping.catchAllName());
            assertThat(request.userId()).isEqualTo(USER_ID);
            assertThat(request.incomingMessageId()).isEqualTo(REFERENCE);
            assertThat(request.currentDate()).isEqualTo(LocalDate.now(clock));
        }

        @Test
        @DisplayName("when a day is stored and no period - then the message is settled RECORDED and counted")
        void whenADayIsStoredAndNoPeriod_thenMessageIsSettledRecordedAndCounted() {
            givenOneDay();

            useCase.handleNext();

            verifySettledAs(ReportOutcome.RECORDED);
            verify(turnMeters).countTurn(ReportOutcome.RECORDED);
        }

        @Test
        @DisplayName("when no day and no period is stored - then the message is settled NOTHING_IDENTIFIED")
        void whenNoDayAndNoPeriodIsStored_thenMessageIsSettledNothingIdentified() {
            useCase.handleNext();

            verifySettledAs(ReportOutcome.NOTHING_IDENTIFIED);
        }

        @Test
        @DisplayName("when no day and one period is stored - then the message is settled ANSWERED")
        void whenNoDayAndOnePeriodIsStored_thenMessageIsSettledAnswered() {
            givenOnePeriod();

            useCase.handleNext();

            verifySettledAs(ReportOutcome.ANSWERED);
        }

        @Test
        @DisplayName("when no day and one period is stored - then the periods under the message are discarded")
        void whenNoDayAndOnePeriodIsStored_thenThePeriodsUnderTheMessageAreDiscarded() {
            givenOnePeriod();

            useCase.handleNext();

            verify(spendingQueryRepository).discard(USER_ID, REFERENCE);
        }

        @Test
        @DisplayName("when discarding the periods fails - then the message is still settled ANSWERED")
        void whenDiscardingThePeriodsFails_thenTheMessageIsStillSettledAnswered() {
            givenOnePeriod();
            when(spendingQueryRepository.discard(anyLong(), any())).thenThrow(persistenceFailure());

            assertThatCode(() -> useCase.handleNext()).doesNotThrowAnyException();

            verifySettledAs(ReportOutcome.ANSWERED);
        }

        @Test
        @DisplayName("when the extraction fails and no day is stored - then the message is settled FAILED")
        void whenExtractionFailsAndNoDayIsStored_thenMessageIsSettledFailed() {
            givenExtractionFails();

            useCase.handleNext();

            verifySettledAs(ReportOutcome.FAILED);
        }

        @Test
        @DisplayName("when the extraction fails and a day is stored - then the message is settled PARTIAL")
        void whenExtractionFailsAndADayIsStored_thenMessageIsSettledPartial() {
            givenExtractionFails();
            givenOneDay();

            useCase.handleNext();

            verifySettledAs(ReportOutcome.PARTIAL);
        }

        @Test
        @DisplayName("when the extraction fails with only a period stored - then the message is settled FAILED")
        void whenExtractionFailsWithOnlyAPeriodStored_thenMessageIsSettledFailed() {
            givenExtractionFails();
            givenOnePeriod();

            useCase.handleNext();

            verifySettledAs(ReportOutcome.FAILED);
        }

        @Test
        @DisplayName("when the groupings read fails - then the message is settled FAILED and counted")
        void whenGroupingsReadFails_thenMessageIsSettledFailedAndCounted() {
            when(groupingRepository.findNamesWithCategories(USER_ID)).thenThrow(persistenceFailure());

            useCase.handleNext();

            verifySettledAs(ReportOutcome.FAILED);
            verify(turnMeters).countTurn(ReportOutcome.FAILED);
            verify(writtenMessageRepository, never()).stampHandOver(anyLong(), any());
            verifyNoInteractions(intentExtractionPort);
        }

        @Test
        @DisplayName("when the groupings lack the catch-all - then the message is settled FAILED and counted")
        void whenGroupingsLackTheCatchAll_thenMessageIsSettledFailedAndCounted() {
            when(groupingRepository.findNamesWithCategories(USER_ID)).thenReturn(List.of("Food", "Auto"));

            useCase.handleNext();

            verifySettledAs(ReportOutcome.FAILED);
            verify(turnMeters).countTurn(ReportOutcome.FAILED);
            verify(writtenMessageRepository, never()).stampHandOver(anyLong(), any());
            verifyNoInteractions(intentExtractionPort);
        }

        @Test
        @DisplayName("when the default currency read fails - then the extraction request carries no currency")
        void whenDefaultCurrencyReadFails_thenTheExtractionRequestCarriesNoCurrency() {
            when(userPreferenceRepository.findDefaultCurrency(USER_ID)).thenThrow(persistenceFailure());

            useCase.handleNext();

            ArgumentCaptor<IntentExtractionRequest> captor = ArgumentCaptor.forClass(IntentExtractionRequest.class);
            verify(intentExtractionPort).extract(captor.capture());
            assertThat(captor.getValue().defaultCurrency()).isEmpty();
        }

        @Test
        @DisplayName("when the stamp answers false - then it answers true and neither extracts nor settles")
        void whenStampAnswersFalse_thenAnswersTrueAndNeitherExtractsNorSettles() {
            when(writtenMessageRepository.stampHandOver(MESSAGE_ID, NOW)).thenReturn(false);

            boolean handled = useCase.handleNext();

            assertThat(handled).isTrue();
            verifyNoInteractions(intentExtractionPort);
            verify(writtenMessageRepository, never()).settle(anyLong(), any(), any());
        }

        @Test
        @DisplayName("when the stamp throws PersistenceFailedException - then no extraction and the message is FAILED")
        void whenStampThrows_thenNoExtractionAndTheMessageIsSettledFailed() {
            when(writtenMessageRepository.stampHandOver(MESSAGE_ID, NOW)).thenThrow(persistenceFailure());

            useCase.handleNext();

            verifyNoInteractions(intentExtractionPort);
            verifySettledAs(ReportOutcome.FAILED);
        }

        @Test
        @DisplayName("when the settle throws PersistenceFailedException - then it reaches the caller")
        void whenSettleThrows_thenItReachesTheCaller() {
            PersistenceFailedException failure = persistenceFailure();
            when(writtenMessageRepository.settle(anyLong(), any(), any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handleNext()).isSameAs(failure);
        }

        @Test
        @DisplayName("when the settle answers false - then nothing is thrown and no turn is counted")
        void whenSettleAnswersFalse_thenNothingIsThrownAndNoTurnIsCounted() {
            when(writtenMessageRepository.settle(anyLong(), any(), any())).thenReturn(false);

            assertThatCode(() -> useCase.handleNext()).doesNotThrowAnyException();

            verify(writtenMessageRepository).settle(anyLong(), any(), any());
            verifyNoInteractions(turnMeters);
        }

        @Test
        @DisplayName("when the days read throws PersistenceFailedException - then it reaches the caller unsettled")
        void whenDaysReadThrows_thenItReachesTheCallerAndTheMessageIsNeverSettled() {
            PersistenceFailedException failure = persistenceFailure();
            when(expenseRepository.findDaysByMessageReference(USER_ID, REFERENCE))
                    .thenThrow(failure);

            assertThatThrownBy(() -> useCase.handleNext()).isSameAs(failure);

            verify(writtenMessageRepository, never()).settle(anyLong(), any(), any());
        }
    }
}
