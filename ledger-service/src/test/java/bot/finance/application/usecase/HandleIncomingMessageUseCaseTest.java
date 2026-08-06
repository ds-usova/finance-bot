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
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.SpendingSummary;
import bot.finance.application.dto.TurnReport;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.domain.exception.CatchAllGroupingMissingException;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Grouping;
import bot.finance.domain.value.MessageReference;
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
    private static final String TEXT = "spent 12 on coffee";

    private Logger log;
    private InitializeUserPort initializeUserPort;
    private GroupingRepository groupingRepository;
    private IntentExtractionPort intentExtractionPort;
    private ExpenseProposalRepository expenseProposalRepository;
    private MessageDeliveryPort messageDeliveryPort;
    private Clock clock;
    private SpendingQueryRepository spendingQueryRepository;
    private ExpenseRepository expenseRepository;
    private HandleIncomingMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(HandleIncomingMessageUseCase.class)).thenReturn(log);
        initializeUserPort = mock(InitializeUserPort.class);
        groupingRepository = mock(GroupingRepository.class);
        intentExtractionPort = mock(IntentExtractionPort.class);
        expenseProposalRepository = mock(ExpenseProposalRepository.class);
        messageDeliveryPort = mock(MessageDeliveryPort.class);
        clock = Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);
        spendingQueryRepository = mock(SpendingQueryRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        when(spendingQueryRepository.findPeriodsByMessageReference(anyLong(), any()))
                .thenReturn(List.of());
        when(expenseRepository.totalsByCurrency(anyLong(), any())).thenReturn(List.of());
        useCase = new HandleIncomingMessageUseCase(
                initializeUserPort,
                groupingRepository,
                intentExtractionPort,
                expenseProposalRepository,
                messageDeliveryPort,
                clock,
                spendingQueryRepository,
                expenseRepository,
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

    @Nested
    @DisplayName("handling an incoming message")
    class Handle {

        @Test
        @DisplayName("when the command is null - then throws InvalidIncomingMessageException and logs nothing "
                + "and none of the three ports is called")
        void whenCommandIsNull_thenThrowsInvalidIncomingMessageExceptionAndLogsNothing() {
            assertThatThrownBy(() -> useCase.handle(null)).isInstanceOf(InvalidIncomingMessageException.class);

            verifyNoInteractions(log);
            verifyNoInteractions(initializeUserPort);
            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(expenseProposalRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when handle is called - then initialize is called with an InitializeUserCommand carrying "
                + "the command's user external id, the extraction request carries a non-null message reference, "
                + "and findSummariesByMessageReference is called with the user's id and that same reference")
        void whenHandleIsCalled_thenInitializeAndExtractionAndLookupCarryUserAndReference() {
            List<String> categoryGroupings = stubKnownUserAndGroupings();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(twoSummaries());

            useCase.handle(newCommand());

            ArgumentCaptor<InitializeUserCommand> initializeCaptor =
                    ArgumentCaptor.forClass(InitializeUserCommand.class);
            verify(initializeUserPort).initialize(initializeCaptor.capture());
            assertThat(initializeCaptor.getValue().externalId()).isEqualTo(EXTERNAL_ID);

            verify(groupingRepository).findNamesWithCategories(USER_ID);

            ArgumentCaptor<IntentExtractionRequest> extractCaptor =
                    ArgumentCaptor.forClass(IntentExtractionRequest.class);
            verify(intentExtractionPort).extract(extractCaptor.capture());
            IntentExtractionRequest request = extractCaptor.getValue();
            assertThat(request.text()).isEqualTo(TEXT);
            assertThat(request.categoryGroupings()).isEqualTo(categoryGroupings);
            assertThat(request.catchAllGrouping()).isEqualTo(Grouping.catchAllName());
            assertThat(request.defaultCurrency()).isEmpty();
            assertThat(request.userExternalId()).isEqualTo(EXTERNAL_ID);
            assertThat(request.currentDate()).isEqualTo(LocalDate.now(clock));
            MessageReference reference = request.messageReference();
            assertThat(reference).isNotNull();

            verify(expenseProposalRepository).findSummariesByMessageReference(USER_ID, reference);
            verify(spendingQueryRepository).findPeriodsByMessageReference(USER_ID, reference);
        }

        @Test
        @DisplayName("when the stored user's grouping names include Grouping.catchAllName() - then the "
                + "extraction request carries exactly those grouping names and that name as its catch-all")
        void
                whenGroupingNamesIncludeCatchAllGroupingName_thenExtractionRequestCarriesThoseNamesAndThatNameAsCatchAll() {
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            List<String> categoryGroupings = List.of("Food", Grouping.catchAllName(), "Auto");
            when(groupingRepository.findNamesWithCategories(USER_ID)).thenReturn(categoryGroupings);
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            useCase.handle(newCommand());

            ArgumentCaptor<IntentExtractionRequest> extractCaptor =
                    ArgumentCaptor.forClass(IntentExtractionRequest.class);
            verify(intentExtractionPort).extract(extractCaptor.capture());
            IntentExtractionRequest request = extractCaptor.getValue();
            assertThat(request.categoryGroupings()).isEqualTo(categoryGroupings);
            assertThat(request.catchAllGrouping()).isEqualTo(Grouping.catchAllName());
        }

        @Test
        @DisplayName("when the stored user's grouping names do not include Grouping.catchAllName() - then "
                + "CatchAllGroupingMissingException propagates and the extraction port is never called")
        void whenGroupingNamesExcludeCatchAllGroupingName_thenCatchAllGroupingMissingExceptionPropagates() {
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
                + "propagates and the extraction port is never called")
        void whenFindNamesWithCategoriesReturnsEmptyList_thenCatchAllGroupingMissingExceptionPropagates() {
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            when(groupingRepository.findNamesWithCategories(USER_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> useCase.handle(newCommand())).isInstanceOf(CatchAllGroupingMissingException.class);

            verifyNoInteractions(intentExtractionPort);
        }

        @Test
        @DisplayName("when extraction returns normally and summaries are present - then deliver receives a "
                + "RECORDED report carrying the command's conversation and inbound message ids, those summaries "
                + "in order, and the same MessageReference the captured IntentExtractionRequest carried")
        void whenExtractionSucceedsWithSummaries_thenDeliverReceivesRecordedReport() {
            stubKnownUserAndGroupings();
            List<ProposalSummary> summaries = twoSummaries();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(summaries);

            useCase.handle(newCommand());

            ArgumentCaptor<IntentExtractionRequest> extractCaptor =
                    ArgumentCaptor.forClass(IntentExtractionRequest.class);
            verify(intentExtractionPort).extract(extractCaptor.capture());
            MessageReference reference = extractCaptor.getValue().messageReference();

            ArgumentCaptor<TurnReport> reportCaptor = ArgumentCaptor.forClass(TurnReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            TurnReport report = reportCaptor.getValue();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.RECORDED);
            assertThat(report.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(report.inboundMessageId()).isEqualTo(INBOUND_MESSAGE_ID);
            assertThat(report.proposals()).containsExactlyElementsOf(summaries);
            assertThat(report.reference()).isEqualTo(reference);
            assertThat(report.summaries()).isEmpty();
        }

        @Test
        @DisplayName("when extraction returns normally and the repository returns an empty list - then deliver "
                + "receives a NOTHING_IDENTIFIED report with no proposals")
        void whenExtractionSucceedsWithNoSummaries_thenDeliverReceivesNothingIdentifiedReport() {
            stubKnownUserAndGroupings();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            useCase.handle(newCommand());

            ArgumentCaptor<TurnReport> reportCaptor = ArgumentCaptor.forClass(TurnReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            TurnReport report = reportCaptor.getValue();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.NOTHING_IDENTIFIED);
            assertThat(report.proposals()).isEmpty();
        }

        @Test
        @DisplayName("when extraction throws IntentExtractionFailedException and the repository returns two "
                + "summaries - then no exception escapes and deliver receives a PARTIAL report carrying those "
                + "summaries")
        void whenExtractionFailsWithSummaries_thenDeliverReceivesPartialReport() {
            stubKnownUserAndGroupings();
            IntentExtractionFailedException failure =
                    new IntentExtractionFailedException("turn failed", new RuntimeException());
            doThrow(failure).when(intentExtractionPort).extract(any());
            List<ProposalSummary> summaries = twoSummaries();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(summaries);

            useCase.handle(newCommand());

            ArgumentCaptor<TurnReport> reportCaptor = ArgumentCaptor.forClass(TurnReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            TurnReport report = reportCaptor.getValue();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.PARTIAL);
            assertThat(report.proposals()).containsExactlyElementsOf(summaries);
        }

        @Test
        @DisplayName("when extraction throws IntentExtractionFailedException and the repository returns an "
                + "empty list - then no exception escapes and deliver receives a FAILED report")
        void whenExtractionFailsWithNoSummaries_thenDeliverReceivesFailedReport() {
            stubKnownUserAndGroupings();
            IntentExtractionFailedException failure =
                    new IntentExtractionFailedException("turn failed", new RuntimeException());
            doThrow(failure).when(intentExtractionPort).extract(any());
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            useCase.handle(newCommand());

            ArgumentCaptor<TurnReport> reportCaptor = ArgumentCaptor.forClass(TurnReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            assertThat(reportCaptor.getValue().outcome()).isEqualTo(ReportOutcome.FAILED);
        }

        @Test
        @DisplayName("when extraction fails - then the error line does not carry the user's own words")
        void whenExtractionFails_thenErrorLineOmitsTheMessageText() {
            stubKnownUserAndGroupings();
            IntentExtractionFailedException failure =
                    new IntentExtractionFailedException("turn failed", new RuntimeException());
            doThrow(failure).when(intentExtractionPort).extract(any());
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
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
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(twoSummaries());

            useCase.handle(newCommand());

            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(log).info(messageCaptor.capture(), argsCaptor.capture());
            assertThat(argsCaptor.getValue()).doesNotContain(TEXT);
            assertThat(messageCaptor.getValue()).doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when initializeUserPort.initialize throws PersistenceFailedException - then the exception "
                + "propagates and neither the repository, the extraction port, the expense proposal repository "
                + "nor the delivery port is called")
        void whenInitializeThrowsPersistenceFailedException_thenExceptionPropagatesAndRemainingPortsUntouched() {
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(initializeUserPort.initialize(any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(expenseProposalRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when groupingRepository.findNamesWithCategories throws PersistenceFailedException - then the "
                + "exception propagates and the extraction port, the expense proposal repository and the "
                + "delivery port are never called")
        void
                whenFindNamesWithCategoriesThrowsPersistenceFailedException_thenExceptionPropagatesAndExtractionPortUntouched() {
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(groupingRepository.findNamesWithCategories(USER_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(expenseProposalRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when findSummariesByMessageReference throws PersistenceFailedException - then that "
                + "exception propagates and deliver is never called")
        void whenFindSummariesThrowsPersistenceFailedException_thenExceptionPropagatesAndDeliverUntouched() {
            stubKnownUserAndGroupings();
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(messageDeliveryPort);
            verifyNoInteractions(spendingQueryRepository);
            verifyNoInteractions(expenseRepository);
        }

        @Test
        @DisplayName("when deliver throws MessageDeliveryFailedException - then that exception propagates")
        void whenDeliverThrowsMessageDeliveryFailedException_thenExceptionPropagates() {
            stubKnownUserAndGroupings();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(twoSummaries());
            MessageDeliveryFailedException failure =
                    new MessageDeliveryFailedException("delivery failed", new RuntimeException());
            doThrow(failure).when(messageDeliveryPort).deliver(any(TurnReport.class));

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);
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

            ArgumentCaptor<MessageReference> referenceCaptor = ArgumentCaptor.forClass(MessageReference.class);
            verify(spendingQueryRepository).discard(eq(USER_ID), referenceCaptor.capture());
            verify(spendingQueryRepository).findPeriodsByMessageReference(USER_ID, referenceCaptor.getValue());
        }

        @Test
        @DisplayName("when the report cannot be delivered - then the periods asked about are kept, so a turn nobody "
                + "was told about leaves its record behind")
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
        }

        @Test
        @DisplayName("when the report is delivered but discarding the periods fails - then the turn still succeeds, "
                + "since the user already has the report")
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
        @DisplayName("when extraction throws InvalidExtractionRequestException - then that exception propagates "
                + "past the catch and deliver is never called")
        void whenExtractionThrowsInvalidExtractionRequestException_thenExceptionPropagatesAndDeliverUntouched() {
            stubKnownUserAndGroupings();
            InvalidExtractionRequestException failure = new InvalidExtractionRequestException("bad request");
            doThrow(failure).when(intentExtractionPort).extract(any());

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when the periods read back name two distinct periods and each totals something - then the "
                + "delivered report carries one summary per period, in the order the read answered them, each "
                + "holding the totals ExpenseRepository answered for it")
        void whenPeriodsReadBackNameTwoDistinctPeriods_thenReportCarriesOneSummaryPerPeriodInOrderWithTotals() {
            stubKnownUserAndGroupings();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
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

            ArgumentCaptor<TurnReport> reportCaptor = ArgumentCaptor.forClass(TurnReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            assertThat(reportCaptor.getValue().summaries())
                    .containsExactly(
                            new SpendingSummary(firstPeriod, firstTotals),
                            new SpendingSummary(secondPeriod, secondTotals));
        }

        @Test
        @DisplayName("when the periods read back name one period the ledger holds nothing in - then the delivered "
                + "report carries that period as a summary with no totals, not an absent one")
        void whenPeriodsReadBackNameOnePeriodHoldingNothing_thenReportCarriesSummaryWithNoTotals() {
            stubKnownUserAndGroupings();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(period));
            when(expenseRepository.totalsByCurrency(USER_ID, period)).thenReturn(List.of());

            useCase.handle(newCommand());

            ArgumentCaptor<TurnReport> reportCaptor = ArgumentCaptor.forClass(TurnReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            assertThat(reportCaptor.getValue().summaries()).containsExactly(new SpendingSummary(period, List.of()));
        }

        @Test
        @DisplayName("when no proposal and one summary were produced by a completed extraction - then the "
                + "report's outcome is ANSWERED")
        void whenNoProposalAndOneSummaryExtractionCompleted_thenOutcomeIsAnswered() {
            stubKnownUserAndGroupings();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(period));
            when(expenseRepository.totalsByCurrency(USER_ID, period)).thenReturn(oneTotal());

            useCase.handle(newCommand());

            ArgumentCaptor<TurnReport> reportCaptor = ArgumentCaptor.forClass(TurnReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            assertThat(reportCaptor.getValue().outcome()).isEqualTo(ReportOutcome.ANSWERED);
        }

        @Test
        @DisplayName("when one proposal and one summary were produced by a completed extraction - then the "
                + "report's outcome is RECORDED and the report carries both lists")
        void whenOneProposalAndOneSummaryExtractionCompleted_thenOutcomeIsRecordedAndCarriesBothLists() {
            stubKnownUserAndGroupings();
            List<ProposalSummary> proposals = List.of(twoSummaries().get(0));
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(proposals);
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(period));
            List<CurrencyTotal> totals = oneTotal();
            when(expenseRepository.totalsByCurrency(USER_ID, period)).thenReturn(totals);

            useCase.handle(newCommand());

            ArgumentCaptor<TurnReport> reportCaptor = ArgumentCaptor.forClass(TurnReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            TurnReport report = reportCaptor.getValue();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.RECORDED);
            assertThat(report.proposals()).containsExactlyElementsOf(proposals);
            assertThat(report.summaries()).containsExactly(new SpendingSummary(period, totals));
        }

        @Test
        @DisplayName("when extraction failed and one summary was recorded, with no proposal - then the report's "
                + "outcome is PARTIAL and it carries that summary")
        void whenExtractionFailedAndOneSummaryRecordedWithNoProposal_thenOutcomeIsPartialAndCarriesThatSummary() {
            stubKnownUserAndGroupings();
            IntentExtractionFailedException failure =
                    new IntentExtractionFailedException("turn failed", new RuntimeException());
            doThrow(failure).when(intentExtractionPort).extract(any());
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of(period));
            List<CurrencyTotal> totals = oneTotal();
            when(expenseRepository.totalsByCurrency(USER_ID, period)).thenReturn(totals);

            useCase.handle(newCommand());

            ArgumentCaptor<TurnReport> reportCaptor = ArgumentCaptor.forClass(TurnReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            TurnReport report = reportCaptor.getValue();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.PARTIAL);
            assertThat(report.summaries()).containsExactly(new SpendingSummary(period, totals));
        }

        @Test
        @DisplayName("when the period read-back throws PersistenceFailedException - then the exception propagates "
                + "and deliver is never called")
        void whenPeriodReadBackThrowsPersistenceFailedException_thenExceptionPropagatesAndDeliverUntouched() {
            stubKnownUserAndGroupings();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
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
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
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
        @DisplayName("when a stored user's id differs from the external id on the command - then both the period "
                + "read-back and every totals read carry that stored user's id and the reference the turn minted")
        void whenStoredUsersIdDiffersFromExternalId_thenPeriodReadBackAndTotalsReadCarryStoredUsersIdAndReference() {
            long differentUserId = 42L;
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(differentUserId, EXTERNAL_ID));
            List<String> categoryGroupings = List.of("Food", "Auto", Grouping.catchAllName());
            when(groupingRepository.findNamesWithCategories(differentUserId)).thenReturn(categoryGroupings);
            when(expenseProposalRepository.findSummariesByMessageReference(eq(differentUserId), any()))
                    .thenReturn(List.of());
            SpendingPeriod period = periodOf("2026-07-01", "2026-07-07");
            when(spendingQueryRepository.findPeriodsByMessageReference(eq(differentUserId), any()))
                    .thenReturn(List.of(period));
            when(expenseRepository.totalsByCurrency(differentUserId, period)).thenReturn(oneTotal());

            useCase.handle(newCommand());

            ArgumentCaptor<IntentExtractionRequest> extractCaptor =
                    ArgumentCaptor.forClass(IntentExtractionRequest.class);
            verify(intentExtractionPort).extract(extractCaptor.capture());
            MessageReference reference = extractCaptor.getValue().messageReference();

            verify(spendingQueryRepository).findPeriodsByMessageReference(differentUserId, reference);
            verify(expenseRepository).totalsByCurrency(differentUserId, period);
        }
    }
}
