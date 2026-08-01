package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.dto.InitializeUserCommand;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.dto.KnownCategory;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HandleIncomingMessageUseCaseTest {

    private static final long USER_ID = 1L;
    private static final String EXTERNAL_ID = "555";
    private static final String CONVERSATION_ID = "555";
    private static final String TEXT = "spent 12 on coffee";

    private Logger log;
    private InitializeUserPort initializeUserPort;
    private CategoryRepository categoryRepository;
    private IntentExtractionPort intentExtractionPort;
    private HandleIncomingMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(HandleIncomingMessageUseCase.class)).thenReturn(log);
        initializeUserPort = mock(InitializeUserPort.class);
        categoryRepository = mock(CategoryRepository.class);
        intentExtractionPort = mock(IntentExtractionPort.class);
        useCase = new HandleIncomingMessageUseCase(
                initializeUserPort, categoryRepository, intentExtractionPort, loggerFactory);
    }

    private HandleIncomingMessageCommand newCommand() {
        return new HandleIncomingMessageCommand(CONVERSATION_ID, TEXT);
    }

    private List<KnownCategory> stubKnownUserAndCategories() {
        when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
        List<KnownCategory> knownCategories =
                List.of(new KnownCategory("Coffee", "Food"), new KnownCategory("Fuel", "Auto"));
        when(categoryRepository.findKnownCategories(USER_ID)).thenReturn(knownCategories);
        return knownCategories;
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
        }

        @Test
        @DisplayName("when handle is called with a conversation id and text - then initialize is called with an "
                + "InitializeUserCommand carrying the conversation id, findKnownCategories is called with the "
                + "user's id, and extract is called with a request carrying that text, those two categories, an "
                + "empty default currency and the user's external id")
        void whenHandleIsCalled_thenPortsAreCalledInOrderWithExpectedArguments() {
            List<KnownCategory> knownCategories = stubKnownUserAndCategories();

            useCase.handle(newCommand());

            ArgumentCaptor<InitializeUserCommand> initializeCaptor =
                    ArgumentCaptor.forClass(InitializeUserCommand.class);
            verify(initializeUserPort).initialize(initializeCaptor.capture());
            assertThat(initializeCaptor.getValue().externalId()).isEqualTo(CONVERSATION_ID);

            verify(categoryRepository).findKnownCategories(USER_ID);

            ArgumentCaptor<IntentExtractionRequest> extractCaptor =
                    ArgumentCaptor.forClass(IntentExtractionRequest.class);
            verify(intentExtractionPort).extract(extractCaptor.capture());
            IntentExtractionRequest request = extractCaptor.getValue();
            assertThat(request.text()).isEqualTo(TEXT);
            assertThat(request.knownCategories()).isEqualTo(knownCategories);
            assertThat(request.defaultCurrency()).isEmpty();
            assertThat(request.userExternalId()).isEqualTo(EXTERNAL_ID);
        }

        @Test
        @DisplayName("when handle completes - then an info line names the conversation id and does not carry the "
                + "message text")
        void whenHandleCompletes_thenInfoLineNamesConversationIdAndOmitsText() {
            stubKnownUserAndCategories();

            useCase.handle(newCommand());

            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(log).info(messageCaptor.capture(), argsCaptor.capture());
            assertThat(argsCaptor.getValue()).contains(CONVERSATION_ID);
            assertThat(argsCaptor.getValue()).doesNotContain(TEXT);
            assertThat(messageCaptor.getValue()).doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when initializeUserPort.initialize throws PersistenceFailedException - then the exception "
                + "propagates and neither the repository nor the extraction port is called")
        void whenInitializeThrowsPersistenceFailedException_thenExceptionPropagatesAndRemainingPortsUntouched() {
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(initializeUserPort.initialize(any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(categoryRepository);
            verifyNoInteractions(intentExtractionPort);
        }

        @Test
        @DisplayName("when categoryRepository.findKnownCategories throws PersistenceFailedException - then the "
                + "exception propagates and the extraction port is never called")
        void whenFindKnownCategoriesThrowsPersistenceFailedException_thenExceptionPropagatesAndExtractionPortUntouched() {
            when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(categoryRepository.findKnownCategories(USER_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(intentExtractionPort);
        }

        @Test
        @DisplayName("when intentExtractionPort.extract throws IntentExtractionFailedException - then the "
                + "exception propagates and no \"handled\" line is logged")
        void whenExtractThrowsIntentExtractionFailedException_thenExceptionPropagatesAndNoHandledLineLogged() {
            stubKnownUserAndCategories();
            IntentExtractionFailedException failure =
                    new IntentExtractionFailedException("turn failed", new RuntimeException());
            doThrow(failure).when(intentExtractionPort).extract(any());

            assertThatThrownBy(() -> useCase.handle(newCommand())).isSameAs(failure);

            verifyNoInteractions(log);
        }
    }
}
