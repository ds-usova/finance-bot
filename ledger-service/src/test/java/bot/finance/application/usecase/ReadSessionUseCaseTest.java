package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ReadSessionCommand;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReadSessionUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;

    private UserRepository userRepository;
    private ReadSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        useCase = new ReadSessionUseCase(userRepository);
    }

    private ReadSessionCommand newCommand() {
        return new ReadSessionCommand(new AuthenticatedUserId(USER_ID));
    }

    @Nested
    @DisplayName("reading a session")
    class Read {

        @Test
        @DisplayName("when a stored user is found under the command's id - then that stored user is answered")
        void whenStoredUserFoundUnderCommandsId_thenRepositoryAskedByThatIdAndStoredUserAnswered() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.requireById(USER_ID)).thenReturn(storedUser);

            User result = useCase.read(newCommand());

            assertThat(result).isSameAs(storedUser);
        }

        @Test
        @DisplayName("when no user row is stored under the command's id - then throws EntityNotFoundException and "
                + "nothing else is read")
        void whenNoUserRowStoredUnderCommandsId_thenThrowsEntityNotFoundExceptionAndNothingElseIsRead() {
            when(userRepository.requireById(USER_ID))
                    .thenThrow(new EntityNotFoundException("user", "no user stored under id " + USER_ID));

            assertThatThrownBy(() -> useCase.read(newCommand())).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("when the command is null - then throws InvalidUserException")
        void whenCommandIsNull_thenThrowsInvalidUserException() {
            assertThatThrownBy(() -> useCase.read(null)).isInstanceOf(InvalidUserException.class);

            verifyNoInteractions(userRepository);
        }
    }
}
