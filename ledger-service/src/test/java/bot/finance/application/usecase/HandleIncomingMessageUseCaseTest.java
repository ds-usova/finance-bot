package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.CurrencyTotal;
import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.dto.InitializeUserCommand;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.ReportLocation;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.SpendingSummary;
import bot.finance.application.dto.TurnReport;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.domain.exception.CatchAllGroupingMissingException;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ProposalReport;
import bot.finance.domain.model.User;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Grouping;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.SpendingPeriod;
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

class HandleIncomingMessageUseCaseTest {

    private static final long USER_ID = 1L;
    private static final String EXTERNAL_ID = "555";
    private static final String CONVERSATION_ID = "777";
    private static final String INBOUND_MESSAGE_ID = "1";
    private static final String SENT_MESSAGE_ID = "999";
    private static final String TEXT = "spent 12 on coffee";

    private Logger log;
    private InitializeUserPort initializeUserPort;
    private GroupingRepository groupingRepository;
    private IntentExtractionPort intentExtractionPort;
    private MessageDeliveryPort messageDeliveryPort;
    private Clock clock;
    private SpendingQueryRepository spendingQueryRepository;
    private ExpenseRepository expenseRepository;
    private ProposalReportRepository proposalReportRepository;
    private HandleIncomingMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(HandleIncomingMessageUseCase.class)).thenReturn(log);
        initializeUserPort = mock(InitializeUserPort.class);
        groupingRepository = mock(GroupingRepository.class);
        intentExtractionPort = mock(IntentExtractionPort.class);
        messageDeliveryPort = mock(MessageDeliveryPort.class);
        clock = Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);
        spendingQueryRepository = mock(SpendingQueryRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        proposalReportRepository = mock(ProposalReportRepository.class);
        when(spendingQueryRepository.findPeriodsByMessageReference(anyLong(), any()))
                .thenReturn(List.of());
        when(expenseRepository.totalsByCurrency(anyLong(), any())).thenReturn(List.of());
        when(messageDeliveryPort.deliver(any())).thenReturn(Optional.empty());
        useCase = new HandleIncomingMessageUseCase(
                initializeUserPort,
                groupingRepository,
                intentExtractionPort,
                messageDeliveryPort,
                clock,
                spendingQueryRepository,
                expenseRepository,
                proposalReportRepository,
                loggerFactory);
    }

    private HandleIncomingMessageCommand newCommand() {
        return new HandleIncomingMessageCommand(EXTERNAL_ID, CONVERSATION_ID, INBOUND_MESSAGE_ID, TEXT);
    }

    private List<String> stubKnownUserAndGroupings() {
        when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
        List<String> categoryGroupings = List.of("Food", "Auto", Grouping.catchAllName());
        when(groupingRepository.findNamesWithCategories(USER_ID)).thenReturn(categoryGroupings);
        return categoryGroupings;
    }

    private List<ProposalSummary> twoSummaries() {
        return List.of(
                new ProposalSummary(
                        "Coffee", "Food", "espresso", Optional.of("Starbucks"), new Money(500, CurrencyCode.of("USD"))),
                new ProposalSummary("Fuel", "Auto", "gas", Optional.empty(), new Money(4000, CurrencyCode.of("USD"))));
    }

    private SpendingPeriod periodOf(String from, String to) {
        return new SpendingPeriod(LocalDate.parse(from), LocalDate.parse(to));
    }

    private List<CurrencyTotal> oneTotal() {
        return List.of(new CurrencyTotal(new Money(500, CurrencyCode.of("USD")), 1));
    }

    private IntentExtractionRequest capturedExtractionRequest() {
        ArgumentCaptor<IntentExtractionRequest> extractCaptor = ArgumentCaptor.forClass(IntentExtractionRequest.class);
        verify(intentExtractionPort).extract(extractCaptor.capture());
        return extractCaptor.getValue();
    }

    private TurnReport deliveredReport() {
        ArgumentCaptor<TurnReport> reportCaptor = ArgumentCaptor.forClass(TurnReport.class);
        verify(messageDeliveryPort).deliver(reportCaptor.capture());
        return reportCaptor.getValue();
    }

    @Nested
    @DisplayName("handling an incoming message")
    class Handle {

        @Test
        @DisplayName("when the command is null - then throws InvalidIncomingMessageException and no port is called")
        void whenCommandIsNull_thenThrowsInvalidIncomingMessageExceptionAndNoPortIsCalled() {
            assertThatThrownBy(() -> useCase.handle(null)).isInstanceOf(InvalidIncomingMessageException.class);

            verifyNoInteractions(log);
            verifyNoInteractions(initializeUserPort);
            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(expenseRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when handle is called - then initialize receives the command's user external id")
        void whenHandleIsCalled_thenInitializeReceivesTheCommandsUserExternalId() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(twoSummaries());

            useCase.handle(newCommand());

            ArgumentCaptor<InitializeUserCommand> initializeCaptor =
                    ArgumentCaptor.forClass(InitializeUserCommand.class);
            verify(initializeUserPort).initialize(initializeCaptor.capture());
            assertThat(initializeCaptor.getValue().externalId()).isEqualTo(EXTERNAL_ID);
        }

        @Test
        @DisplayName("when handle is called - then the extraction request carries the text, groupings, currency "
                + "and date")
        void whenHandleIsCalled_thenExtractionRequestCarriesTextGroupingsCurrencyAndDate() {
            List<String> categoryGroupings = stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(twoSummaries());

            useCase.handle(newCommand());

            verify(groupingRepository).findNamesWithCategories(USER_ID);

            IntentExtractionRequest request = capturedExtractionRequest();
            assertThat(request.text()).isEqualTo(TEXT);
            assertThat(request.categoryGroupings()).isEqualTo(categoryGroupings);
            assertThat(request.catchAllGrouping()).isEqualTo(Grouping.catchAllName());
            assertThat(request.defaultCurrency()).isEmpty();
            assertThat(request.userId()).isEqualTo(USER_ID);
            assertThat(request.currentDate()).isEqualTo(LocalDate.now(clock));
        }

        @Test
        @DisplayName("when handle is called - then the reference derived from the command reaches both read-backs")
        void whenHandleIsCalled_thenTheDerivedReferenceReachesBothReadBacks() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(twoSummaries());

            useCase.handle(newCommand());

            IncomingMessageId derivedReference = IncomingMessageId.of(CONVERSATION_ID, INBOUND_MESSAGE_ID);
            assertThat(capturedExtractionRequest().incomingMessageId()).isEqualTo(derivedReference);

            verify(expenseRepository).findSummariesByMessageReference(USER_ID, derivedReference);
            verify(spendingQueryRepository).findPeriodsByMessageReference(USER_ID, derivedReference);
        }

        @Test
        @DisplayName("when the grouping names include the catch-all - then the request carries those names and "
                + "that catch-all")
        void whenGroupingNamesIncludeCatchAll_thenRequestCarriesThoseNamesAndThatCatchAll() {
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            List<String> categoryGroupings = List.of("Food", Grouping.catchAllName(), "Auto");
            when(groupingRepository.findNamesWithCategories(USER_ID)).thenReturn(categoryGroupings);
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            useCase.handle(newCommand());

            IntentExtractionRequest request = capturedExtractionRequest();
            assertThat(request.categoryGroupings()).isEqualTo(categoryGroupings);
            assertThat(request.catchAllGrouping()).isEqualTo(Grouping.catchAllName());
        }

        @Test
        @DisplayName(
                "when the grouping names exclude the catch-all - then CatchAllGroupingMissingException " + "propagates")
        void whenGroupingNamesExcludeCatchAll_thenCatchAllGroupingMissingExceptionPropagates() {
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            when(groupingRepository.findNamesWithCategories(USER_ID)).thenReturn(List.of("Food", "Auto"));

            assertThatThrownBy(() -> useCase.handle(newCommand()))
                    .isInstanceOf(CatchAllGroupingMissingException.class)
                    .hasMessageContaining(Grouping.catchAllName());

            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when findNamesWithCategories answers an empty list - then CatchAllGroupingMissingException "
                + "propagates")
        void whenFindNamesWithCategoriesReturnsEmptyList_thenCatchAllGroupingMissingExceptionPropagates() {
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            when(groupingRepository.findNamesWithCategories(USER_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> useCase.handle(newCommand())).isInstanceOf(CatchAllGroupingMissingException.class);

            verifyNoInteractions(intentExtractionPort);
        }

        @Test
        @DisplayName("when extraction returns normally and summaries are present - then deliver receives a "
                + "RECORDED report")
        void whenExtractionSucceedsWithSummaries_thenDeliverReceivesRecordedReport() {
            stubKnownUserAndGroupings();
            List<ProposalSummary> summaries = twoSummaries();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(summaries);

            useCase.handle(newCommand());

            IncomingMessageId reference = capturedExtractionRequest().incomingMessageId();

            TurnReport report = deliveredReport();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.RECORDED);
            assertThat(report.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(report.inboundMessageId()).isEqualTo(INBOUND_MESSAGE_ID);
            assertThat(report.proposals()).containsExactlyElementsOf(summaries);
            assertThat(report.reference()).isEqualTo(reference);
            assertThat(report.summaries()).isEmpty();
        }

        @Test
        @DisplayName("when extraction returns normally and nothing was recorded - then deliver receives a "
                + "NOTHING_IDENTIFIED report")
        void whenExtractionSucceedsWithNoSummaries_thenDeliverReceivesNothingIdentifiedReport() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            useCase.handle(newCommand());

            TurnReport report = deliveredReport();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.NOTHING_IDENTIFIED);
            assertThat(report.proposals()).isEmpty();
        }

        @Test
        @DisplayName("when extraction fails and summaries were recorded - then deliver receives a PARTIAL report")
        void whenExtractionFailsWithSummaries_thenDeliverReceivesPartialReport() {
            stubKnownUserAndGroupings();
            IntentExtractionFailedException failure =
                    new IntentExtractionFailedException("turn failed", new RuntimeException());
            doThrow(failure).when(intentExtractionPort).extract(any());
            List<ProposalSummary> summaries = twoSummaries();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(summaries);

            useCase.handle(newCommand());

            TurnReport report = deliveredReport();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.PARTIAL);
            assertThat(report.proposals()).containsExactlyElementsOf(summaries);
        }

        @Test
        @DisplayName("when extraction fails and nothing was recorded - then deliver receives a FAILED report")
        void whenExtractionFailsWithNoSummaries_thenDeliverReceivesFailedReport() {
            stubKnownUserAndGroupings();
            IntentExtractionFailedException failure =
                    new IntentExtractionFailedException("turn failed", new RuntimeException());
            doThrow(failure).when(intentExtractionPort).extract(any());
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            useCase.handle(newCommand());

            assertThat(deliveredReport().outcome()).isEqualTo(ReportOutcome.FAILED);
        }

        @Test
        @DisplayName("when extraction fails - then the error line does not carry the user's own words")
        void whenExtractionFails_thenErrorLineOmitsTheMessageText() {
            stubKnownUserAndGroupings();
            IntentExtractionFailedException failure =
                    new IntentExtractionFailedException("turn failed", new RuntimeException());
            doThrow(failure).when(intentExtractionPort).extract(any());
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            useCase.handle(newCommand());

            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(log).error(messageCaptor.capture(), argsCaptor.capture());
            assertThat(argsCaptor.getValue()).doesNotContain(TEXT);
            assertThat(messageCaptor.getValue()).doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when a turn succeeds - then the info line does not carry the user's own words")
        void whenTurnSucceeds_thenInfoLineOmitsTheMessageText() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(twoSummaries());

            useCase.handle(newCommand());

            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(log).info(messageCaptor.capture(), argsCaptor.capture());
            assertThat(argsCaptor.getValue()).doesNotContain(TEXT);
            assertThat(messageCaptor.getValue()).doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when initialize throws PersistenceFailedException - then it propagates and no further port "
                + "is called")
        void whenInitializeThrowsPersistenceFailedException_thenExceptionPropagatesAndRemainingPortsUntouched() {
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(initializeUserPort.initialize(any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(expenseRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when findNamesWithCategories throws PersistenceFailedException - then it propagates and no "
                + "further port is called")
        void whenFindNamesWithCategoriesThrowsPersistenceFailedException_thenExceptionPropagates() {
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(groupingRepository.findNamesWithCategories(USER_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(expenseRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when findSummariesByMessageReference throws PersistenceFailedException - then it propagates")
        void whenFindSummariesThrowsPersistenceFailedException_thenExceptionPropagatesAndDeliverUntouched() {
            stubKnownUserAndGroupings();
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(messageDeliveryPort);
            verifyNoInteractions(spendingQueryRepository);
        }

        @Test
        @DisplayName("when deliver throws MessageDeliveryFailedException - then that exception propagates")
        void whenDeliverThrowsMessageDeliveryFailedException_thenExceptionPropagates() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(twoSummaries());
            MessageDeliveryFailedException failure =
                    new MessageDeliveryFailedException("delivery failed", new RuntimeException());
            doThrow(failure).when(messageDeliveryPort).deliver(any(TurnReport.class));

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(proposalReportRepository);
        }

        @Test
        @DisplayName(
                "when delivery answers a location - then one report row is stored for the turn's own message " + "id")
        void whenDeliveryAnswersALocation_thenOneReportRowIsStoredForTheTurnsOwnMessageId() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            ReportLocation location = new ReportLocation(CONVERSATION_ID, SENT_MESSAGE_ID);
            when(messageDeliveryPort.deliver(any())).thenReturn(Optional.of(location));

            useCase.handle(newCommand());

            ArgumentCaptor<ProposalReport> reportCaptor = ArgumentCaptor.forClass(ProposalReport.class);
            verify(proposalReportRepository).store(reportCaptor.capture());
            ProposalReport stored = reportCaptor.getValue();
            assertThat(stored.userId()).isEqualTo(USER_ID);
            assertThat(stored.incomingMessageId()).isEqualTo(IncomingMessageId.of(CONVERSATION_ID, INBOUND_MESSAGE_ID));
            assertThat(stored.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(stored.sentMessageId()).isEqualTo(SENT_MESSAGE_ID);
        }

        @Test
        @DisplayName("when delivery answers nothing - then no report row is stored and the turn still succeeds")
        void whenDeliveryAnswersNothing_thenNoReportRowIsStoredAndTurnStillSucceeds() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            assertThatCode(() -> useCase.handle(newCommand())).doesNotThrowAnyException();

            verifyNoInteractions(proposalReportRepository);
        }

        @Test
        @DisplayName("when the report row cannot be stored - then the turn still succeeds and nothing propagates")
        void whenReportRowCannotBeStored_thenTurnStillSucceedsAndNothingPropagates() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            ReportLocation location = new ReportLocation(CONVERSATION_ID, SENT_MESSAGE_ID);
            when(messageDeliveryPort.deliver(any())).thenReturn(Optional.of(location));
            when(proposalReportRepository.store(any()))
                    .thenThrow(new PersistenceFailedException("store failed", new RuntimeException()));

            assertThatCode(() -> useCase.handle(newCommand())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the report carrying a period reaches the user - then the periods asked about under that "
                + "message are discarded")
        void whenReportReachesTheUser_thenPeriodsAskedAboutAreDiscarded() {
            stubKnownUserAndGroupings();
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(period));
            when(expenseRepository.totalsByCurrency(USER_ID, period)).thenReturn(oneTotal());

            useCase.handle(newCommand());

            ArgumentCaptor<IncomingMessageId> referenceCaptor = ArgumentCaptor.forClass(IncomingMessageId.class);
            verify(spendingQueryRepository).discard(eq(USER_ID), referenceCaptor.capture());
            verify(spendingQueryRepository).findPeriodsByMessageReference(USER_ID, referenceCaptor.getValue());
        }

        @Test
        @DisplayName("when the report cannot be delivered - then the periods asked about are kept")
        void whenReportCannotBeDelivered_thenPeriodsAskedAboutAreKept() {
            stubKnownUserAndGroupings();
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(period));
            when(expenseRepository.totalsByCurrency(USER_ID, period)).thenReturn(oneTotal());
            doThrow(new MessageDeliveryFailedException("delivery failed", new RuntimeException()))
                    .when(messageDeliveryPort)
                    .deliver(any(TurnReport.class));

            assertThatThrownBy(() -> useCase.handle(newCommand())).isInstanceOf(MessageDeliveryFailedException.class);

            verify(spendingQueryRepository, never()).discard(anyLong(), any());
            verifyNoInteractions(proposalReportRepository);
        }

        @Test
        @DisplayName("when discarding the periods fails after delivery - then the turn still succeeds")
        void whenDiscardingFailsAfterDelivery_thenTurnStillSucceeds() {
            stubKnownUserAndGroupings();
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(period));
            when(expenseRepository.totalsByCurrency(USER_ID, period)).thenReturn(oneTotal());
            when(spendingQueryRepository.discard(anyLong(), any()))
                    .thenThrow(new PersistenceFailedException("discard failed", new RuntimeException()));

            assertThatCode(() -> useCase.handle(newCommand())).doesNotThrowAnyException();

            verify(messageDeliveryPort).deliver(any(TurnReport.class));
        }

        @Test
        @DisplayName("when the message asked about no period - then nothing is discarded")
        void whenMessageAskedAboutNoPeriod_thenNothingIsDiscarded() {
            stubKnownUserAndGroupings();
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            useCase.handle(newCommand());

            verify(spendingQueryRepository, never()).discard(anyLong(), any());
        }

        @Test
        @DisplayName("when extraction throws InvalidExtractionRequestException - then it propagates past the catch")
        void whenExtractionThrowsInvalidExtractionRequestException_thenExceptionPropagatesAndDeliverUntouched() {
            stubKnownUserAndGroupings();
            InvalidExtractionRequestException failure = new InvalidExtractionRequestException("bad request");
            doThrow(failure).when(intentExtractionPort).extract(any());

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when the periods read back name two distinct periods - then the report carries one summary "
                + "per period")
        void whenPeriodsReadBackNameTwoDistinctPeriods_thenReportCarriesOneSummaryPerPeriod() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            SpendingPeriod firstPeriod = periodOf("2026-07-01", "2026-07-07");
            SpendingPeriod secondPeriod = periodOf("2026-07-08", "2026-07-14");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(firstPeriod, secondPeriod));
            List<CurrencyTotal> firstTotals = List.of(new CurrencyTotal(new Money(500, CurrencyCode.of("USD")), 1));
            List<CurrencyTotal> secondTotals = List.of(new CurrencyTotal(new Money(1200, CurrencyCode.of("EUR")), 2));
            when(expenseRepository.totalsByCurrency(USER_ID, firstPeriod)).thenReturn(firstTotals);
            when(expenseRepository.totalsByCurrency(USER_ID, secondPeriod)).thenReturn(secondTotals);

            useCase.handle(newCommand());

            assertThat(deliveredReport().summaries())
                    .containsExactly(
                            new SpendingSummary(firstPeriod, firstTotals),
                            new SpendingSummary(secondPeriod, secondTotals));
        }

        @Test
        @DisplayName("when a period holds nothing - then the report carries it as a summary with no totals")
        void whenPeriodHoldsNothing_thenReportCarriesItAsASummaryWithNoTotals() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(period));
            when(expenseRepository.totalsByCurrency(USER_ID, period)).thenReturn(List.of());

            useCase.handle(newCommand());

            assertThat(deliveredReport().summaries()).containsExactly(new SpendingSummary(period, List.of()));
        }

        @Test
        @DisplayName("when no proposal and one summary were produced by a completed extraction - then the "
                + "report's outcome is ANSWERED")
        void whenNoProposalAndOneSummaryExtractionCompleted_thenOutcomeIsAnswered() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(period));
            when(expenseRepository.totalsByCurrency(USER_ID, period)).thenReturn(oneTotal());

            useCase.handle(newCommand());

            assertThat(deliveredReport().outcome()).isEqualTo(ReportOutcome.ANSWERED);
        }

        @Test
        @DisplayName("when a completed extraction produced a proposal and a summary - then the report is RECORDED "
                + "and carries both")
        void whenCompletedExtractionProducedAProposalAndASummary_thenReportIsRecordedAndCarriesBoth() {
            stubKnownUserAndGroupings();
            List<ProposalSummary> proposals = List.of(twoSummaries().get(0));
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(proposals);
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(period));
            List<CurrencyTotal> totals = oneTotal();
            when(expenseRepository.totalsByCurrency(USER_ID, period)).thenReturn(totals);

            useCase.handle(newCommand());

            TurnReport report = deliveredReport();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.RECORDED);
            assertThat(report.proposals()).containsExactlyElementsOf(proposals);
            assertThat(report.summaries()).containsExactly(new SpendingSummary(period, totals));
        }

        @Test
        @DisplayName(
                "when extraction failed and one summary was recorded - then the report is PARTIAL and " + "carries it")
        void whenExtractionFailedAndOneSummaryWasRecorded_thenReportIsPartialAndCarriesIt() {
            stubKnownUserAndGroupings();
            IntentExtractionFailedException failure =
                    new IntentExtractionFailedException("turn failed", new RuntimeException());
            doThrow(failure).when(intentExtractionPort).extract(any());
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(period));
            List<CurrencyTotal> totals = oneTotal();
            when(expenseRepository.totalsByCurrency(USER_ID, period)).thenReturn(totals);

            useCase.handle(newCommand());

            TurnReport report = deliveredReport();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.PARTIAL);
            assertThat(report.summaries()).containsExactly(new SpendingSummary(period, totals));
        }

        @Test
        @DisplayName("when the period read-back throws PersistenceFailedException - then the exception propagates "
                + "and deliver is never called")
        void whenPeriodReadBackThrowsPersistenceFailedException_thenExceptionPropagatesAndDeliverUntouched() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            PersistenceFailedException failure = new PersistenceFailedException("read failed", new RuntimeException());
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when the totals read throws PersistenceFailedException - then the exception propagates and "
                + "deliver is never called")
        void whenTotalsReadThrowsPersistenceFailedException_thenExceptionPropagatesAndDeliverUntouched() {
            stubKnownUserAndGroupings();
            when(expenseRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(period));
            PersistenceFailedException failure = new PersistenceFailedException("read failed", new RuntimeException());
            when(expenseRepository.totalsByCurrency(USER_ID, period)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when the stored user's id differs from the external id - then every read carries that stored id")
        void whenStoredUsersIdDiffersFromExternalId_thenEveryReadCarriesThatStoredId() {
            long differentUserId = 42L;
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(differentUserId, EXTERNAL_ID));
            List<String> categoryGroupings = List.of("Food", "Auto", Grouping.catchAllName());
            when(groupingRepository.findNamesWithCategories(differentUserId)).thenReturn(categoryGroupings);
            when(expenseRepository.findSummariesByMessageReference(eq(differentUserId), any()))
                    .thenReturn(List.of());
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(differentUserId), any()))
                    .thenReturn(List.of(period));
            when(expenseRepository.totalsByCurrency(differentUserId, period)).thenReturn(oneTotal());

            useCase.handle(newCommand());

            IntentExtractionRequest request = capturedExtractionRequest();
            IncomingMessageId reference = request.incomingMessageId();
            assertThat(request.userId()).isEqualTo(differentUserId);

            verify(groupingRepository).findNamesWithCategories(differentUserId);
            verify(expenseRepository).findSummariesByMessageReference(differentUserId, reference);
            verify(spendingQueryRepository).findPeriodsByMessageReference(differentUserId, reference);
            verify(expenseRepository).totalsByCurrency(differentUserId, period);
        }
    }
}
