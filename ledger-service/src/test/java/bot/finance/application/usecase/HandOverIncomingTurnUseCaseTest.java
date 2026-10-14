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
import bot.finance.application.dto.TurnReport;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.application.port.UserPreferenceRepository;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.IntentExtractionRefusedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.IncomingTurn;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Grouping;
import bot.finance.domain.value.IncomingMessageId;
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

class HandOverIncomingTurnUseCaseTest {

    private static final long USER_ID = 7L;
    private static final long TURN_ID = 5L;
    private static final String CONVERSATION_ID = "777";
    private static final String INBOUND_MESSAGE_ID = "1";
    private static final String TEXT = "spent 12 on coffee";
    private static final IncomingMessageId REFERENCE = IncomingMessageId.of("777:1");
    private static final Instant KEPT_AT = Instant.parse("2026-08-04T23:50:00Z");
    private static final Instant NOW = Instant.parse("2026-08-05T00:10:00Z");
    private static final Duration MAX_QUEUE_WAIT = Duration.ofMinutes(10);

    private IncomingTurnRepository incomingTurnRepository;
    private GroupingRepository groupingRepository;
    private UserPreferenceRepository userPreferenceRepository;
    private IntentExtractionPort intentExtractionPort;
    private ExpenseRepository expenseRepository;
    private SpendingQueryRepository spendingQueryRepository;
    private MessageDeliveryPort messageDeliveryPort;
    private ProposalReportRepository proposalReportRepository;
    private TurnMeters turnMeters;
    private Logger log;
    private List<CatalogueEntry> catalogue;
    private IncomingTurn turn;
    private HandOverIncomingTurnUseCase useCase;

    @BeforeEach
    void setUp() {
        incomingTurnRepository = mock(IncomingTurnRepository.class);
        groupingRepository = mock(GroupingRepository.class);
        userPreferenceRepository = mock(UserPreferenceRepository.class);
        intentExtractionPort = mock(IntentExtractionPort.class);
        expenseRepository = mock(ExpenseRepository.class);
        spendingQueryRepository = mock(SpendingQueryRepository.class);
        messageDeliveryPort = mock(MessageDeliveryPort.class);
        proposalReportRepository = mock(ProposalReportRepository.class);
        turnMeters = mock(TurnMeters.class);
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(HandOverIncomingTurnUseCase.class)).thenReturn(log);
        useCase = new HandOverIncomingTurnUseCase(
                incomingTurnRepository,
                groupingRepository,
                userPreferenceRepository,
                intentExtractionPort,
                expenseRepository,
                spendingQueryRepository,
                messageDeliveryPort,
                proposalReportRepository,
                turnMeters,
                Clock.fixed(NOW, ZoneOffset.UTC),
                MAX_QUEUE_WAIT,
                loggerFactory);

        turn = IncomingTurn.stored(
                TURN_ID,
                USER_ID,
                CONVERSATION_ID,
                INBOUND_MESSAGE_ID,
                TEXT,
                KEPT_AT,
                Optional.empty(),
                Optional.empty());
        catalogue = catalogueOf(List.of("Food", "Auto", Grouping.catchAllName()));
        when(incomingTurnRepository.findOldestQueuedKeptSince(NOW.minus(MAX_QUEUE_WAIT)))
                .thenReturn(Optional.of(turn));
        when(groupingRepository.findCatalogue(USER_ID)).thenReturn(catalogue);
        when(userPreferenceRepository.findDefaultCurrency(USER_ID)).thenReturn(Optional.empty());
        when(incomingTurnRepository.markHandedOver(TURN_ID, NOW)).thenReturn(true);
        when(incomingTurnRepository.delete(TURN_ID)).thenReturn(true);
        when(messageDeliveryPort.deliver(any())).thenReturn(Optional.empty());
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
    @DisplayName("handing over the next turn")
    class HandOverNext {

        @Test
        @DisplayName("when no turn is queued - then it answers false and asks the store since the wait before now")
        void whenNoTurnIsQueued_thenItAnswersFalseAndAsksTheStoreSinceTheWaitBeforeNow() {
            when(incomingTurnRepository.findOldestQueuedKeptSince(any())).thenReturn(Optional.empty());

            boolean handedOver = useCase.handOverNext();

            assertThat(handedOver).isFalse();
            verify(incomingTurnRepository).findOldestQueuedKeptSince(NOW.minus(MAX_QUEUE_WAIT));
            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(userPreferenceRepository);
            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(expenseRepository);
            verifyNoInteractions(spendingQueryRepository);
            verifyNoInteractions(messageDeliveryPort);
            verifyNoInteractions(proposalReportRepository);
            verifyNoInteractions(turnMeters);
        }

        @Test
        @DisplayName("when a turn is queued - then the request carries the turn, its catalogue and the day it was kept")
        void whenATurnIsQueued_thenTheRequestCarriesTheTurnItsCatalogueAndTheDayItWasKept() {
            useCase.handOverNext();

            IntentExtractionRequest request = extractedRequest();
            assertThat(request.text()).isEqualTo(TEXT);
            assertThat(request.catalogue()).isEqualTo(catalogue);
            assertThat(request.catchAllGrouping()).isEqualTo(Grouping.catchAllName());
            assertThat(request.userId()).isEqualTo(USER_ID);
            assertThat(request.incomingMessageId()).isEqualTo(REFERENCE);
            assertThat(request.currentDate()).isEqualTo(LocalDate.parse("2026-08-04"));
            assertThat(request.defaultCurrency()).isEmpty();
        }

        @Test
        @DisplayName("when a turn is queued - then the attempt is recorded at the clock's instant before the hand-over")
        void whenATurnIsQueued_thenTheAttemptIsRecordedAtTheClocksInstantBeforeTheHandOver() {
            useCase.handOverNext();

            InOrder order = inOrder(incomingTurnRepository, intentExtractionPort);
            order.verify(incomingTurnRepository).recordAttempt(TURN_ID, NOW);
            order.verify(intentExtractionPort).extract(any(IntentExtractionRequest.class));
        }

        @Test
        @DisplayName("when the stored preference holds EUR - then the request carries EUR as the default currency")
        void whenTheStoredPreferenceHoldsEur_thenTheRequestCarriesEurAsTheDefaultCurrency() {
            when(userPreferenceRepository.findDefaultCurrency(USER_ID)).thenReturn(Optional.of(CurrencyCode.of("EUR")));

            useCase.handOverNext();

            assertThat(extractedRequest().defaultCurrency()).contains(CurrencyCode.of("EUR"));
        }

        @Test
        @DisplayName("when the hand-over returns - then the turn is stamped handed over and the look answers true")
        void whenTheHandOverReturns_thenTheTurnIsStampedHandedOverAndTheLookAnswersTrue() {
            boolean handedOver = useCase.handOverNext();

            assertThat(handedOver).isTrue();
            verify(incomingTurnRepository).markHandedOver(TURN_ID, NOW);
            verify(incomingTurnRepository, never()).delete(anyLong());
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName(
                "when the stamp answers false because the end arrived first - then it answers true and does no more")
        void whenTheStampAnswersFalseBecauseTheEndArrivedFirst_thenItAnswersTrueAndDoesNoMore() {
            when(incomingTurnRepository.markHandedOver(TURN_ID, NOW)).thenReturn(false);

            boolean handedOver = useCase.handOverNext();

            assertThat(handedOver).isTrue();
            verify(incomingTurnRepository).markHandedOver(TURN_ID, NOW);
            verify(incomingTurnRepository, never()).delete(anyLong());
            verifyNoInteractions(messageDeliveryPort);
            verifyNoInteractions(turnMeters);
        }

        @Test
        @DisplayName(
                "when the hand-over is refused and nothing is under the message - then the FAILED report is delivered")
        void whenTheHandOverIsRefusedAndNothingIsUnderTheMessage_thenTheFailedReportIsDelivered() {
            givenRefused();

            boolean handedOver = useCase.handOverNext();

            assertThat(handedOver).isTrue();
            InOrder order = inOrder(incomingTurnRepository, messageDeliveryPort);
            order.verify(incomingTurnRepository).delete(TURN_ID);
            ArgumentCaptor<TurnReport> captor = ArgumentCaptor.forClass(TurnReport.class);
            order.verify(messageDeliveryPort).deliver(captor.capture());
            assertThat(captor.getValue().outcome()).isEqualTo(ReportOutcome.FAILED);
            assertThat(captor.getValue().reference()).isEqualTo(REFERENCE);
            verify(turnMeters).countTurn(ReportOutcome.FAILED);
        }

        @Test
        @DisplayName("when the hand-over is refused - then one error line names the message and the outcome")
        void whenTheHandOverIsRefused_thenOneErrorLineNamesTheMessageAndTheOutcome() {
            givenRefused();

            useCase.handOverNext();

            ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
            verify(log).error(message.capture(), args.capture());
            String line = message.getValue() + " "
                    + Arrays.stream(args.getValue()).map(String::valueOf).collect(Collectors.joining(" "));
            assertThat(line)
                    .contains(REFERENCE.value())
                    .contains(ReportOutcome.FAILED.name())
                    .doesNotContain(TEXT);
        }

        @Test
        @DisplayName(
                "when the hand-over is refused and the delete throws - then it propagates and nothing is delivered")
        void whenTheHandOverIsRefusedAndTheDeleteThrows_thenItPropagatesAndNothingIsDelivered() {
            givenRefused();
            PersistenceFailedException failure = persistenceFailure();
            when(incomingTurnRepository.delete(TURN_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handOverNext()).isSameAs(failure);

            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when the hand-over fails - then it answers false and the turn is neither stamped nor deleted")
        void whenTheHandOverFails_thenItAnswersFalseAndTheTurnIsNeitherStampedNorDeleted() {
            doThrow(new IntentExtractionFailedException("not accepted", new RuntimeException()))
                    .when(intentExtractionPort)
                    .extract(any());

            boolean handedOver = useCase.handOverNext();

            assertThat(handedOver).isFalse();
            verify(incomingTurnRepository).recordAttempt(TURN_ID, NOW);
            verify(intentExtractionPort).extract(any(IntentExtractionRequest.class));
            verify(incomingTurnRepository, never()).markHandedOver(anyLong(), any());
            verify(incomingTurnRepository, never()).delete(anyLong());
        }

        @Test
        @DisplayName("when the catalogue lacks the catch-all - then nothing is recorded or handed over")
        void whenTheCatalogueLacksTheCatchAll_thenNothingIsRecordedOrHandedOver() {
            when(groupingRepository.findCatalogue(USER_ID)).thenReturn(catalogueOf(List.of("Food", "Auto")));

            useCase.handOverNext();

            verify(groupingRepository).findCatalogue(USER_ID);
            verify(incomingTurnRepository, never()).recordAttempt(anyLong(), any());
            verifyNoInteractions(intentExtractionPort);
        }

        @Test
        @DisplayName("when the catalogue lacks the catch-all - then the turn is deleted and counted unreported")
        void whenTheCatalogueLacksTheCatchAll_thenTheTurnIsDeletedAndCountedUnreported() {
            when(groupingRepository.findCatalogue(USER_ID)).thenReturn(catalogueOf(List.of("Food", "Auto")));

            boolean handedOver = useCase.handOverNext();

            assertThat(handedOver).isTrue();
            verify(incomingTurnRepository).delete(TURN_ID);
            verify(turnMeters).countUnreported();
            verifyNoInteractions(messageDeliveryPort);
        }

        @ParameterizedTest
        @EnumSource(Read.class)
        @DisplayName(
                "when a preparing read throws PersistenceFailedException - then it answers false and hands nothing over")
        void whenAPreparingReadThrowsPersistenceFailedException_thenItAnswersFalseAndHandsNothingOver(Read read) {
            switch (read) {
                case CATALOGUE ->
                    when(groupingRepository.findCatalogue(USER_ID)).thenThrow(persistenceFailure());
                case DEFAULT_CURRENCY ->
                    when(userPreferenceRepository.findDefaultCurrency(USER_ID)).thenThrow(persistenceFailure());
            }

            boolean handedOver = useCase.handOverNext();

            assertThat(handedOver).isFalse();
            switch (read) {
                case CATALOGUE -> verify(groupingRepository).findCatalogue(USER_ID);
                case DEFAULT_CURRENCY -> verify(userPreferenceRepository).findDefaultCurrency(USER_ID);
            }
            verify(incomingTurnRepository, never()).recordAttempt(anyLong(), any());
            verifyNoInteractions(intentExtractionPort);
        }

        @Test
        @DisplayName("when recording the attempt throws PersistenceFailedException - then nothing is handed over")
        void whenRecordingTheAttemptThrowsPersistenceFailedException_thenItPropagatesAndNothingIsHandedOver() {
            PersistenceFailedException failure = persistenceFailure();
            doThrow(failure).when(incomingTurnRepository).recordAttempt(TURN_ID, NOW);

            assertThatThrownBy(() -> useCase.handOverNext()).isSameAs(failure);

            verifyNoInteractions(intentExtractionPort);
        }

        @Test
        @DisplayName("when stamping the hand-over throws PersistenceFailedException - then the turn is not deleted")
        void whenStampingTheHandOverThrowsPersistenceFailedException_thenTheTurnIsNotDeletedOrReported() {
            PersistenceFailedException failure = persistenceFailure();
            when(incomingTurnRepository.markHandedOver(TURN_ID, NOW)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handOverNext()).isSameAs(failure);

            verify(intentExtractionPort).extract(any(IntentExtractionRequest.class));
            verify(incomingTurnRepository, never()).delete(anyLong());
            verifyNoInteractions(messageDeliveryPort);
            verifyNoInteractions(turnMeters);
        }
    }
}
