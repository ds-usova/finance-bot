package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.NewUser;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InitializeUserUseCaseTest {

    private static final String EXTERNAL_ID = "555";

    private UserRepository userRepository;
    private Logger log;
    private InitializeUserUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(InitializeUserUseCase.class)).thenReturn(log);
        useCase = new InitializeUserUseCase(userRepository, loggerFactory);
    }

    @Nested
    @DisplayName("initializing a user")
    class Initialize {

        @Test
        @DisplayName("when no user is stored for the external id - then the repository creates a user carrying that "
                + "external id and Category.defaults(), and the created user is returned")
        void whenNoUserExistsForExternalId_thenRepositoryCreatesUserWithDefaultsAndReturnsIt() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());
            User createdUser = User.stored(1L, EXTERNAL_ID);
            when(userRepository.create(any(), any())).thenReturn(createdUser);

            User result = useCase.initialize(new NewUser(EXTERNAL_ID));

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            ArgumentCaptor<List<Category>> categoriesCaptor = ArgumentCaptor.forClass(List.class);
            verify(userRepository).create(userCaptor.capture(), categoriesCaptor.capture());
            assertThat(userCaptor.getValue().externalId()).isEqualTo(EXTERNAL_ID);
            assertThat(categoriesCaptor.getValue()).isEqualTo(Category.defaults());
            assertThat(result).isSameAs(createdUser);
        }

        @Test
        @DisplayName("when a user is already stored for the external id - then that user is returned and the "
                + "repository is never asked to create anything")
        void whenUserAlreadyExistsForExternalId_thenReturnsStoredUserWithoutCreating() {
            User storedUser = User.stored(1L, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));

            User result = useCase.initialize(new NewUser(EXTERNAL_ID));

            assertThat(result).isSameAs(storedUser);
            verify(userRepository, never()).create(any(), any());
        }

        @Test
        @DisplayName(
                "when the command is absent - then throws InvalidUserException and the repository is " + "untouched")
        void whenCommandIsAbsent_thenThrowsInvalidUserExceptionAndRepositoryIsUntouched() {
            assertThatThrownBy(() -> useCase.initialize(null)).isInstanceOf(InvalidUserException.class);

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("when the repository raises PersistenceFailedException while creating - then the "
                + "exception reaches the caller unchanged and is not swallowed or retried")
        void whenRepositoryRaisesPersistenceFailedExceptionOnCreate_thenExceptionPropagatesUnchanged() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());
            PersistenceFailedException failure =
                    new PersistenceFailedException("insert failed", new RuntimeException());
            when(userRepository.create(any(), any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.initialize(new NewUser(EXTERNAL_ID)))
                    .isSameAs(failure);

            verify(userRepository, times(1)).create(any(), any());
        }
    }
}
