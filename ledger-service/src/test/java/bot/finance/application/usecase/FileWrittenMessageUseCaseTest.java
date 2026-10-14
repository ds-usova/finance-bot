package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.FileWrittenMessageCommand;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.TurnHandOverDispatchPort;
import bot.finance.application.port.UserRepository;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.exception.WrittenMessageInProgressException;
import bot.finance.domain.model.User;
import bot.finance.domain.model.WrittenMessage;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class FileWrittenMessageUseCaseTest {

    private static final long CALLER_ID = 5L;
    private static final long STORED_USER_ID = 7L;
    private static final String EXTERNAL_ID = "555";
    private static final String TEXT = "coffee 3.40, taxi home 14";
    private static final Instant NOW = Instant.parse("2026-08-05T10:15:30Z");

    private UserRepository userRepository;
    private WrittenMessageRepository writtenMessageRepository;
    private TurnHandOverDispatchPort dispatchPort;
    private FileWrittenMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        writtenMessageRepository = mock(WrittenMessageRepository.class);
        dispatchPort = mock(TurnHandOverDispatchPort.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(FileWrittenMessageUseCase.class)).thenReturn(mock(Logger.class));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        useCase = new FileWrittenMessageUseCase(
                userRepository, writtenMessageRepository, dispatchPort, clock, loggerFactory);
    }

    private FileWrittenMessageCommand commandOf(String text) {
        return new FileWrittenMessageCommand(new AuthenticatedUserId(CALLER_ID), text);
    }

    private void givenStoredUser() {
        when(userRepository.requireById(CALLER_ID)).thenReturn(User.stored(STORED_USER_ID, EXTERNAL_ID));
    }

    @Nested
    @DisplayName("filing a written message")
    class File {

        @Test
        @DisplayName("when the caller is stored - then a WAITING message is stored and the reader dispatched after")
        void whenCallerIsStored_thenWaitingMessageIsStoredAndReaderDispatchedAfter() {
            givenStoredUser();
            WrittenMessageView stored = new WrittenMessageView(
                    42L, STORED_USER_ID, TEXT, WrittenMessageStatus.WAITING, Optional.empty(), NOW);
            when(writtenMessageRepository.store(any())).thenReturn(stored);

            WrittenMessageView answer = useCase.file(commandOf(TEXT));

            ArgumentCaptor<WrittenMessage> captor = ArgumentCaptor.forClass(WrittenMessage.class);
            InOrder order = inOrder(writtenMessageRepository, dispatchPort);
            order.verify(writtenMessageRepository).store(captor.capture());
            order.verify(dispatchPort).dispatch();
            verify(dispatchPort, times(1)).dispatch();
            WrittenMessage message = captor.getValue();
            assertThat(message.userId()).isEqualTo(STORED_USER_ID);
            assertThat(message.text()).isEqualTo(TEXT);
            assertThat(message.status()).isEqualTo(WrittenMessageStatus.WAITING);
            assertThat(message.createdAt()).isEqualTo(NOW);
            assertThat(answer).isEqualTo(stored);
        }

        @Test
        @DisplayName(
                "when no user row is stored for the caller - then throws EntityNotFoundException and stores nothing")
        void whenNoUserRowIsStored_thenThrowsEntityNotFoundExceptionAndStoresNothing() {
            when(userRepository.requireById(CALLER_ID))
                    .thenThrow(new EntityNotFoundException("user", "no user stored under id " + CALLER_ID));

            assertThatThrownBy(() -> useCase.file(commandOf(TEXT))).isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(writtenMessageRepository);
            verifyNoInteractions(dispatchPort);
        }

        @Test
        @DisplayName("when the text is only spaces - then throws InvalidWrittenMessageException and stores nothing")
        void whenTextIsOnlySpaces_thenThrowsInvalidWrittenMessageExceptionAndStoresNothing() {
            givenStoredUser();

            assertThatThrownBy(() -> useCase.file(commandOf("   "))).isInstanceOf(InvalidWrittenMessageException.class);

            verify(writtenMessageRepository, never()).store(any());
            verifyNoInteractions(dispatchPort);
        }

        @Test
        @DisplayName("when the repository throws WrittenMessageInProgressException - then it reaches the caller")
        void whenRepositoryThrowsInProgress_thenItReachesTheCaller() {
            givenStoredUser();
            WrittenMessageInProgressException failure = new WrittenMessageInProgressException("one is unsettled");
            when(writtenMessageRepository.store(any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.file(commandOf(TEXT))).isSameAs(failure);

            verifyNoInteractions(dispatchPort);
        }

        @Test
        @DisplayName("when the repository throws PersistenceFailedException - then it reaches the caller")
        void whenRepositoryThrowsPersistenceFailed_thenItReachesTheCaller() {
            givenStoredUser();
            PersistenceFailedException failure = new PersistenceFailedException("write failed", new RuntimeException());
            when(writtenMessageRepository.store(any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.file(commandOf(TEXT))).isSameAs(failure);

            verifyNoInteractions(dispatchPort);
        }

        @Test
        @DisplayName("when the command is absent - then throws InvalidWrittenMessageException")
        void whenCommandIsAbsent_thenThrowsInvalidWrittenMessageException() {
            assertThatThrownBy(() -> useCase.file(null)).isInstanceOf(InvalidWrittenMessageException.class);

            verifyNoInteractions(writtenMessageRepository);
            verifyNoInteractions(dispatchPort);
        }
    }
}
