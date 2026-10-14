package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.CatalogueEntry;
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
import bot.finance.domain.exception.IntentExtractionRefusedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Grouping;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Clock;
import java.time.Duration;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class HandleWrittenMessageUseCaseTest {

    private static final long USER_ID = 7L;
    private static final long MESSAGE_ID = 42L;
    private static final String TEXT = "coffee 3.40, taxi home 14";
    private static final Instant FILED_AT = Instant.parse("2026-08-04T23:50:00Z");
    private static final Instant NOW = Instant.parse("2026-08-05T00:10:00Z");
    private static final Duration MAX_QUEUE_WAIT = Duration.ofMinutes(10);
    private static final IncomingMessageId REFERENCE = IncomingMessageId.of("web:42");

    private WrittenMessageRepository writtenMessageRepository;
    private GroupingRepository groupingRepository;
    private UserPreferenceRepository userPreferenceRepository;
    private IntentExtractionPort intentExtractionPort;
    private ExpenseRepository expenseRepository;
    private SpendingQueryRepository spendingQueryRepository;
    private TurnMeters turnMeters;
    private Logger log;
    private List<CatalogueEntry> catalogue;
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
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(HandleWrittenMessageUseCase.class)).thenReturn(log);
        useCase = new HandleWrittenMessageUseCase(
                writtenMessageRepository,
                groupingRepository,
                userPreferenceRepository,
                intentExtractionPort,
                expenseRepository,
                spendingQueryRepository,
                turnMeters,
                Clock.fixed(NOW, ZoneOffset.UTC),
                MAX_QUEUE_WAIT,
                loggerFactory);

        WrittenMessageView waiting = new WrittenMessageView(
                MESSAGE_ID, USER_ID, TEXT, WrittenMessageStatus.WAITING, Optional.empty(), FILED_AT);
        catalogue = catalogueOf(List.of("Food", "Auto", Grouping.catchAllName()));
        when(writtenMessageRepository.findOldestWaitingFiledSince(NOW.minus(MAX_QUEUE_WAIT)))
                .thenReturn(Optional.of(waiting));
        when(groupingRepository.findCatalogue(USER_ID)).thenReturn(catalogue);
        when(userPreferenceRepository.findDefaultCurrency(USER_ID)).thenReturn(Optional.empty());
        when(writtenMessageRepository.markHandedOver(MESSAGE_ID, NOW)).thenReturn(true);
        when(writtenMessageRepository.settle(anyLong(), any(), any())).thenReturn(true);
    }

    private static List<CatalogueEntry> catalogueOf(List<String> groupings) {
        return groupings.stream()
                .map(grouping -> new CatalogueEntry(grouping, List.of("category")))
                .toList();
    }

    private IntentExtractionRequest extractedRequest() {
        ArgumentCaptor<IntentExtractionRequest> captor = ArgumentCaptor.forClass(IntentExtractionRequest.class);
        verify(intentExtractionPort).extract(captor.capture());
        return captor.getValue();
    }

    private void givenRefused() {
        doThrow(new IntentExtractionRefusedException("refused", new RuntimeException()))
                .when(intentExtractionPort)
                .extract(any());
    }

    private PersistenceFailedException persistenceFailure() {
        return new PersistenceFailedException("failed", new RuntimeException());
    }

    enum Read {
        CATALOGUE,
        DEFAULT_CURRENCY
    }

    @Nested
    @DisplayName("handling the next written message")
    class HandleNext {

        @Test
        @DisplayName("when no text is waiting - then it answers false and asks the store since the wait before now")
        void whenNoTextIsWaiting_thenItAnswersFalseAndAsksTheStoreSinceTheWaitBeforeNow() {
            when(writtenMessageRepository.findOldestWaitingFiledSince(any())).thenReturn(Optional.empty());

            boolean handled = useCase.handleNext();

            assertThat(handled).isFalse();
            verify(writtenMessageRepository).findOldestWaitingFiledSince(NOW.minus(MAX_QUEUE_WAIT));
            verify(writtenMessageRepository, never()).recordAttempt(anyLong(), any());
            verify(writtenMessageRepository, never()).markHandedOver(anyLong(), any());
            verify(writtenMessageRepository, never()).settle(anyLong(), any(), any());
            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(userPreferenceRepository);
            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(expenseRepository);
            verifyNoInteractions(spendingQueryRepository);
            verifyNoInteractions(turnMeters);
        }

        @Test
        @DisplayName(
                "when a text is waiting - then the request carries the text, its catalogue and the day it was filed")
        void whenATextIsWaiting_thenTheRequestCarriesTheTextItsCatalogueAndTheDayItWasFiled() {
            when(userPreferenceRepository.findDefaultCurrency(USER_ID)).thenReturn(Optional.of(CurrencyCode.of("EUR")));

            useCase.handleNext();

            IntentExtractionRequest request = extractedRequest();
            assertThat(request.text()).isEqualTo(TEXT);
            assertThat(request.catalogue()).isEqualTo(catalogue);
            assertThat(request.catchAllGrouping()).isEqualTo(Grouping.catchAllName());
            assertThat(request.defaultCurrency()).contains(CurrencyCode.of("EUR"));
            assertThat(request.userId()).isEqualTo(USER_ID);
            assertThat(request.incomingMessageId()).isEqualTo(REFERENCE);
            assertThat(request.currentDate()).isEqualTo(LocalDate.parse("2026-08-04"));
        }

        @Test
        @DisplayName(
                "when a text is waiting - then the attempt is recorded at the clock's instant before the hand-over")
        void whenATextIsWaiting_thenTheAttemptIsRecordedAtTheClocksInstantBeforeTheHandOver() {
            useCase.handleNext();

            InOrder order = inOrder(writtenMessageRepository, intentExtractionPort);
            order.verify(writtenMessageRepository).recordAttempt(MESSAGE_ID, NOW);
            order.verify(intentExtractionPort).extract(any(IntentExtractionRequest.class));
        }

        @Test
        @DisplayName("when the hand-over returns - then the text is moved to READING and the look answers true")
        void whenTheHandOverReturns_thenTheTextIsMovedToReadingAndTheLookAnswersTrue() {
            boolean handled = useCase.handleNext();

            assertThat(handled).isTrue();
            verify(writtenMessageRepository).markHandedOver(MESSAGE_ID, NOW);
            verify(writtenMessageRepository, never()).settle(anyLong(), any(), any());
        }

        @Test
        @DisplayName("when the hand-over is refused and nothing is under the message - then the text is settled FAILED")
        void whenTheHandOverIsRefusedAndNothingIsUnderTheMessage_thenTheTextIsSettledFailed() {
            givenRefused();

            boolean handled = useCase.handleNext();

            assertThat(handled).isTrue();
            verify(writtenMessageRepository).settle(MESSAGE_ID, ReportOutcome.FAILED, NOW);
            verify(turnMeters).countTurn(ReportOutcome.FAILED);
        }

        @Test
        @DisplayName("when the hand-over is refused - then one error line names the message and the outcome")
        void whenTheHandOverIsRefused_thenOneErrorLineNamesTheMessageAndTheOutcome() {
            givenRefused();

            useCase.handleNext();

            ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
            verify(log).error(message.capture(), args.capture());
            String line = message.getValue() + " "
                    + Arrays.stream(args.getValue()).map(String::valueOf).collect(Collectors.joining(" "));
            assertThat(line)
                    .contains(String.valueOf(MESSAGE_ID))
                    .contains(ReportOutcome.FAILED.name())
                    .doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when the hand-over is refused and the settle throws - then it propagates and nothing is counted")
        void whenTheHandOverIsRefusedAndTheSettleThrows_thenItPropagatesAndNothingIsCounted() {
            givenRefused();
            PersistenceFailedException failure = persistenceFailure();
            when(writtenMessageRepository.settle(anyLong(), any(), any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handleNext()).isSameAs(failure);

            verifyNoInteractions(turnMeters);
        }

        @Test
        @DisplayName("when the hand-over fails - then it answers false and the text is neither moved nor settled")
        void whenTheHandOverFails_thenItAnswersFalseAndTheTextIsNeitherMovedNorSettled() {
            doThrow(new IntentExtractionFailedException("not accepted", new RuntimeException()))
                    .when(intentExtractionPort)
                    .extract(any());

            boolean handled = useCase.handleNext();

            assertThat(handled).isFalse();
            verify(writtenMessageRepository).recordAttempt(MESSAGE_ID, NOW);
            verify(intentExtractionPort).extract(any(IntentExtractionRequest.class));
            verify(writtenMessageRepository, never()).markHandedOver(anyLong(), any());
            verify(writtenMessageRepository, never()).settle(anyLong(), any(), any());
        }

        @Test
        @DisplayName("when the catalogue lacks the catch-all - then nothing is recorded or handed over")
        void whenTheCatalogueLacksTheCatchAll_thenNothingIsRecordedOrHandedOver() {
            when(groupingRepository.findCatalogue(USER_ID)).thenReturn(catalogueOf(List.of("Food", "Auto")));

            useCase.handleNext();

            verify(groupingRepository).findCatalogue(USER_ID);
            verify(writtenMessageRepository, never()).recordAttempt(anyLong(), any());
            verifyNoInteractions(intentExtractionPort);
        }

        @Test
        @DisplayName(
                "when the catalogue lacks the catch-all - then the text is settled FAILED, counted, and it answers true")
        void whenTheCatalogueLacksTheCatchAll_thenTheTextIsSettledFailedCountedAndItAnswersTrue() {
            when(groupingRepository.findCatalogue(USER_ID)).thenReturn(catalogueOf(List.of("Food", "Auto")));

            boolean handled = useCase.handleNext();

            assertThat(handled).isTrue();
            verify(writtenMessageRepository).settle(MESSAGE_ID, ReportOutcome.FAILED, NOW);
            verify(turnMeters).countTurn(ReportOutcome.FAILED);
        }

        @ParameterizedTest
        @EnumSource(Read.class)
        @DisplayName("when a preparing read throws PersistenceFailedException - then it answers false and does no more")
        void whenAPreparingReadThrowsPersistenceFailedException_thenItAnswersFalseAndDoesNoMore(Read read) {
            switch (read) {
                case CATALOGUE ->
                    when(groupingRepository.findCatalogue(USER_ID)).thenThrow(persistenceFailure());
                case DEFAULT_CURRENCY ->
                    when(userPreferenceRepository.findDefaultCurrency(USER_ID)).thenThrow(persistenceFailure());
            }

            boolean handled = useCase.handleNext();

            assertThat(handled).isFalse();
            switch (read) {
                case CATALOGUE -> verify(groupingRepository).findCatalogue(USER_ID);
                case DEFAULT_CURRENCY -> verify(userPreferenceRepository).findDefaultCurrency(USER_ID);
            }
            verify(writtenMessageRepository, never()).recordAttempt(anyLong(), any());
            verifyNoInteractions(intentExtractionPort);
            verify(writtenMessageRepository, never()).settle(anyLong(), any(), any());
        }

        @Test
        @DisplayName("when recording the attempt throws PersistenceFailedException - then nothing is handed over")
        void whenRecordingTheAttemptThrowsPersistenceFailedException_thenNothingIsHandedOver() {
            PersistenceFailedException failure = persistenceFailure();
            doThrow(failure).when(writtenMessageRepository).recordAttempt(MESSAGE_ID, NOW);

            assertThatThrownBy(() -> useCase.handleNext()).isSameAs(failure);

            verifyNoInteractions(intentExtractionPort);
        }

        @Test
        @DisplayName("when moving the text to READING throws PersistenceFailedException - then it is never settled")
        void whenMovingTheTextToReadingThrowsPersistenceFailedException_thenItIsNeverSettled() {
            PersistenceFailedException failure = persistenceFailure();
            when(writtenMessageRepository.markHandedOver(MESSAGE_ID, NOW)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handleNext()).isSameAs(failure);

            verify(intentExtractionPort).extract(any(IntentExtractionRequest.class));
            verify(writtenMessageRepository, never()).settle(anyLong(), any(), any());
        }
    }
}
