package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HandleIncomingMessageUseCaseTest {

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
    }
}
