package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.IncomingMessage;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HandleIncomingMessageUseCaseTest {

    private static final String CONVERSATION_ID = "555";
    private static final String TEXT = "lunch 12 euro";

    private Logger log;
    private HandleIncomingMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(HandleIncomingMessageUseCase.class)).thenReturn(log);
        useCase = new HandleIncomingMessageUseCase(loggerFactory);
    }

    @Nested
    @DisplayName("handling an incoming message")
    class Handle {

        @Test
        @DisplayName("when the command carries a conversation id and text - then both are logged at info level")
        void whenCommandCarriesConversationIdAndText_thenLogsBothAtInfoLevel() {
            useCase.handle(new IncomingMessage(CONVERSATION_ID, TEXT));

            ArgumentCaptor<Object[]> loggedArguments = ArgumentCaptor.forClass(Object[].class);
            verify(log).info(anyString(), loggedArguments.capture());
            assertThat(loggedArguments.getValue()).contains(CONVERSATION_ID, TEXT);
        }

        @Test
        @DisplayName("when the command is null - then throws InvalidIncomingMessageException and logs nothing")
        void whenCommandIsNull_thenThrowsInvalidIncomingMessageExceptionAndLogsNothing() {
            assertThatThrownBy(() -> useCase.handle(null)).isInstanceOf(InvalidIncomingMessageException.class);

            verifyNoInteractions(log);
        }
    }
}
