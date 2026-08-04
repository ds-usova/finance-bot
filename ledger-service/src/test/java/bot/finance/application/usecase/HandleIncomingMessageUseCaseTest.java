package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.dto.InitializeUserCommand;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.dto.ProposalReport;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.domain.exception.CatchAllGroupingMissingException;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
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
    private CategoryRepository categoryRepository;
    private IntentExtractionPort intentExtractionPort;
    private ExpenseProposalRepository expenseProposalRepository;
    private MessageDeliveryPort messageDeliveryPort;
    private HandleIncomingMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(HandleIncomingMessageUseCase.class)).thenReturn(log);
        initializeUserPort = mock(InitializeUserPort.class);
        categoryRepository = mock(CategoryRepository.class);
        intentExtractionPort = mock(IntentExtractionPort.class);
        expenseProposalRepository = mock(ExpenseProposalRepository.class);
        messageDeliveryPort = mock(MessageDeliveryPort.class);
        useCase = new HandleIncomingMessageUseCase(
                initializeUserPort,
                categoryRepository,
                intentExtractionPort,
                expenseProposalRepository,
                messageDeliveryPort,
                loggerFactory);
    }

    private HandleIncomingMessageCommand newCommand() {
        return new HandleIncomingMessageCommand(EXTERNAL_ID, CONVERSATION_ID, INBOUND_MESSAGE_ID, TEXT);
    }

    private List<String> stubKnownUserAndCategories() {
        when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
        List<String> categoryGroupings = List.of("Food", "Auto", Category.catchAllGroupingName());
        when(categoryRepository.findGroupingNames(USER_ID)).thenReturn(categoryGroupings);
        return categoryGroupings;
    }

    private List<ProposalSummary> twoSummaries() {
        return List.of(
                new ProposalSummary(
                        "Coffee", "Food", "espresso", Optional.of("Starbucks"), new Money(500, CurrencyCode.of("USD"))),
                new ProposalSummary("Fuel", "Auto", "gas", Optional.empty(), new Money(4000, CurrencyCode.of("USD"))));
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
            verifyNoInteractions(categoryRepository);
            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(expenseProposalRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when handle is called - then initialize is called with an InitializeUserCommand carrying "
                + "the command's user external id, the extraction request carries a non-null message reference, "
                + "and findSummariesByMessageReference is called with the user's id and that same reference")
        void whenHandleIsCalled_thenInitializeAndExtractionAndLookupCarryUserAndReference() {
            List<String> categoryGroupings = stubKnownUserAndCategories();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(twoSummaries());

            useCase.handle(newCommand());

            ArgumentCaptor<InitializeUserCommand> initializeCaptor =
                    ArgumentCaptor.forClass(InitializeUserCommand.class);
            verify(initializeUserPort).initialize(initializeCaptor.capture());
            assertThat(initializeCaptor.getValue().externalId()).isEqualTo(EXTERNAL_ID);

            verify(categoryRepository).findGroupingNames(USER_ID);

            ArgumentCaptor<IntentExtractionRequest> extractCaptor =
                    ArgumentCaptor.forClass(IntentExtractionRequest.class);
            verify(intentExtractionPort).extract(extractCaptor.capture());
            IntentExtractionRequest request = extractCaptor.getValue();
            assertThat(request.text()).isEqualTo(TEXT);
            assertThat(request.categoryGroupings()).isEqualTo(categoryGroupings);
            assertThat(request.catchAllGrouping()).isEqualTo(Category.catchAllGroupingName());
            assertThat(request.defaultCurrency()).isEmpty();
            assertThat(request.userExternalId()).isEqualTo(EXTERNAL_ID);
            MessageReference reference = request.messageReference();
            assertThat(reference).isNotNull();

            verify(expenseProposalRepository).findSummariesByMessageReference(USER_ID, reference);
        }

        @Test
        @DisplayName("when the stored user's grouping names include Category.catchAllGroupingName() - then the "
                + "extraction request carries exactly those grouping names and that name as its catch-all")
        void
                whenGroupingNamesIncludeCatchAllGroupingName_thenExtractionRequestCarriesThoseNamesAndThatNameAsCatchAll() {
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            List<String> categoryGroupings = List.of("Food", Category.catchAllGroupingName(), "Auto");
            when(categoryRepository.findGroupingNames(USER_ID)).thenReturn(categoryGroupings);
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            useCase.handle(newCommand());

            ArgumentCaptor<IntentExtractionRequest> extractCaptor =
                    ArgumentCaptor.forClass(IntentExtractionRequest.class);
            verify(intentExtractionPort).extract(extractCaptor.capture());
            IntentExtractionRequest request = extractCaptor.getValue();
            assertThat(request.categoryGroupings()).isEqualTo(categoryGroupings);
            assertThat(request.catchAllGrouping()).isEqualTo(Category.catchAllGroupingName());
        }

        @Test
        @DisplayName("when the stored user's grouping names do not include Category.catchAllGroupingName() - then "
                + "CatchAllGroupingMissingException propagates and the extraction port is never called")
        void whenGroupingNamesExcludeCatchAllGroupingName_thenCatchAllGroupingMissingExceptionPropagates() {
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            when(categoryRepository.findGroupingNames(USER_ID)).thenReturn(List.of("Food", "Auto"));

            assertThatThrownBy(() -> useCase.handle(newCommand()))
                    .isInstanceOf(CatchAllGroupingMissingException.class)
                    .hasMessageContaining(Category.catchAllGroupingName());

            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when findGroupingNames answers an empty list - then CatchAllGroupingMissingException "
                + "propagates and the extraction port is never called")
        void whenFindGroupingNamesReturnsEmptyList_thenCatchAllGroupingMissingExceptionPropagates() {
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            when(categoryRepository.findGroupingNames(USER_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> useCase.handle(newCommand())).isInstanceOf(CatchAllGroupingMissingException.class);

            verifyNoInteractions(intentExtractionPort);
        }

        @Test
        @DisplayName("when extraction returns normally and summaries are present - then deliver receives a "
                + "RECORDED report carrying the command's conversation and inbound message ids and those "
                + "summaries in order")
        void whenExtractionSucceedsWithSummaries_thenDeliverReceivesRecordedReport() {
            stubKnownUserAndCategories();
            List<ProposalSummary> summaries = twoSummaries();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(summaries);

            useCase.handle(newCommand());

            ArgumentCaptor<ProposalReport> reportCaptor = ArgumentCaptor.forClass(ProposalReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            ProposalReport report = reportCaptor.getValue();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.RECORDED);
            assertThat(report.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(report.inboundMessageId()).isEqualTo(INBOUND_MESSAGE_ID);
            assertThat(report.proposals()).containsExactlyElementsOf(summaries);
        }

        @Test
        @DisplayName("when extraction returns normally and the repository returns an empty list - then deliver "
                + "receives a NOTHING_IDENTIFIED report with no proposals")
        void whenExtractionSucceedsWithNoSummaries_thenDeliverReceivesNothingIdentifiedReport() {
            stubKnownUserAndCategories();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            useCase.handle(newCommand());

            ArgumentCaptor<ProposalReport> reportCaptor = ArgumentCaptor.forClass(ProposalReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            ProposalReport report = reportCaptor.getValue();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.NOTHING_IDENTIFIED);
            assertThat(report.proposals()).isEmpty();
        }

        @Test
        @DisplayName("when extraction throws IntentExtractionFailedException and the repository returns two "
                + "summaries - then no exception escapes and deliver receives a PARTIAL report carrying those "
                + "summaries")
        void whenExtractionFailsWithSummaries_thenDeliverReceivesPartialReport() {
            stubKnownUserAndCategories();
            IntentExtractionFailedException failure =
                    new IntentExtractionFailedException("turn failed", new RuntimeException());
            doThrow(failure).when(intentExtractionPort).extract(any());
            List<ProposalSummary> summaries = twoSummaries();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(summaries);

            useCase.handle(newCommand());

            ArgumentCaptor<ProposalReport> reportCaptor = ArgumentCaptor.forClass(ProposalReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            ProposalReport report = reportCaptor.getValue();
            assertThat(report.outcome()).isEqualTo(ReportOutcome.PARTIAL);
            assertThat(report.proposals()).containsExactlyElementsOf(summaries);
        }

        @Test
        @DisplayName("when extraction throws IntentExtractionFailedException and the repository returns an "
                + "empty list - then no exception escapes and deliver receives a FAILED report")
        void whenExtractionFailsWithNoSummaries_thenDeliverReceivesFailedReport() {
            stubKnownUserAndCategories();
            IntentExtractionFailedException failure =
                    new IntentExtractionFailedException("turn failed", new RuntimeException());
            doThrow(failure).when(intentExtractionPort).extract(any());
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            useCase.handle(newCommand());

            ArgumentCaptor<ProposalReport> reportCaptor = ArgumentCaptor.forClass(ProposalReport.class);
            verify(messageDeliveryPort).deliver(reportCaptor.capture());
            assertThat(reportCaptor.getValue().outcome()).isEqualTo(ReportOutcome.FAILED);
        }

        @Test
        @DisplayName("when extraction throws IntentExtractionFailedException - then an error line is logged "
                + "carrying the message reference and the outcome, and not the message text")
        void whenExtractionFails_thenErrorLineNamesReferenceAndOutcomeAndOmitsText() {
            stubKnownUserAndCategories();
            IntentExtractionFailedException failure =
                    new IntentExtractionFailedException("turn failed", new RuntimeException());
            doThrow(failure).when(intentExtractionPort).extract(any());
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(List.of());

            useCase.handle(newCommand());

            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(log).error(messageCaptor.capture(), argsCaptor.capture());
            assertThat(argsCaptor.getValue()).contains(ReportOutcome.FAILED);
            assertThat(argsCaptor.getValue()).anyMatch(arg -> arg instanceof MessageReference);
            assertThat(argsCaptor.getValue()).doesNotContain(TEXT);
            assertThat(messageCaptor.getValue()).doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when a turn succeeds - then an info line names the message reference and the user's "
                + "external id, and does not carry the message text")
        void whenTurnSucceeds_thenInfoLineNamesReferenceAndExternalIdAndOmitsText() {
            stubKnownUserAndCategories();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(twoSummaries());

            useCase.handle(newCommand());

            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(log).info(messageCaptor.capture(), argsCaptor.capture());
            assertThat(argsCaptor.getValue()).contains(EXTERNAL_ID);
            assertThat(argsCaptor.getValue()).anyMatch(arg -> arg instanceof MessageReference);
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

            verifyNoInteractions(categoryRepository);
            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(expenseProposalRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when categoryRepository.findGroupingNames throws PersistenceFailedException - then the "
                + "exception propagates and the extraction port, the expense proposal repository and the "
                + "delivery port are never called")
        void whenFindGroupingNamesThrowsPersistenceFailedException_thenExceptionPropagatesAndExtractionPortUntouched() {
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(categoryRepository.findGroupingNames(USER_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(intentExtractionPort);
            verifyNoInteractions(expenseProposalRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when findSummariesByMessageReference throws PersistenceFailedException - then that "
                + "exception propagates and deliver is never called")
        void whenFindSummariesThrowsPersistenceFailedException_thenExceptionPropagatesAndDeliverUntouched() {
            stubKnownUserAndCategories();
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when deliver throws MessageDeliveryFailedException - then that exception propagates")
        void whenDeliverThrowsMessageDeliveryFailedException_thenExceptionPropagates() {
            stubKnownUserAndCategories();
            when(expenseProposalRepository.findSummariesByMessageReference(eq(USER_ID), any()))
                    .thenReturn(twoSummaries());
            MessageDeliveryFailedException failure =
                    new MessageDeliveryFailedException("delivery failed", new RuntimeException());
            doThrow(failure).when(messageDeliveryPort).deliver(any());

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);
        }

        @Test
        @DisplayName("when extraction throws InvalidExtractionRequestException - then that exception propagates "
                + "past the catch and deliver is never called")
        void whenExtractionThrowsInvalidExtractionRequestException_thenExceptionPropagatesAndDeliverUntouched() {
            stubKnownUserAndCategories();
            InvalidExtractionRequestException failure = new InvalidExtractionRequestException("bad request");
            doThrow(failure).when(intentExtractionPort).extract(any());

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(messageDeliveryPort);
        }
    }
}
