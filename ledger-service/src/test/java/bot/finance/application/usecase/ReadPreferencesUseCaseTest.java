package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.Preferences;
import bot.finance.application.dto.ReadPreferencesCommand;
import bot.finance.application.port.UserPreferenceRepository;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReadPreferencesUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;

    private UserRepository userRepository;
    private UserPreferenceRepository userPreferenceRepository;
    private ReadPreferencesUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userPreferenceRepository = mock(UserPreferenceRepository.class);
        useCase = new ReadPreferencesUseCase(userRepository, userPreferenceRepository);
    }

    private ReadPreferencesCommand newCommand() {
        return new ReadPreferencesCommand(new AuthenticatedUserId(USER_ID));
    }

    @Nested
    @DisplayName("reading preferences")
    class Read {

        @Test
        @DisplayName("when the caller's stored preference holds a currency - then the answer carries it, read for the "
                + "stored id")
        void whenCallerStoredPreferenceHoldsCurrency_thenAnswerCarriesItAndRepositoryAskedByStoredId() {
            when(userRepository.requireById(USER_ID)).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            when(userPreferenceRepository.findDefaultCurrency(USER_ID)).thenReturn(Optional.of(CurrencyCode.of("EUR")));

            Preferences result = useCase.read(newCommand());

            assertThat(result.defaultCurrency()).contains(CurrencyCode.of("EUR"));
            verify(userPreferenceRepository).findDefaultCurrency(USER_ID);
        }

        @Test
        @DisplayName("when the caller has no preference row - then the answer carries no currency")
        void whenCallerHasNoPreferenceRow_thenAnswerCarriesNoCurrency() {
            when(userRepository.requireById(USER_ID)).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            when(userPreferenceRepository.findDefaultCurrency(USER_ID)).thenReturn(Optional.empty());

            Preferences result = useCase.read(newCommand());

            assertThat(result.defaultCurrency()).isEmpty();
        }

        @Test
        @DisplayName("when the user store lacks the caller - then EntityNotFoundException propagates, preference "
                + "port untouched")
        void whenUserStoreDoesNotHoldCaller_thenEntityNotFoundExceptionPropagatesAndPreferencePortUntouched() {
            when(userRepository.requireById(USER_ID))
                    .thenThrow(new EntityNotFoundException("user", "no user stored under id " + USER_ID));

            assertThatThrownBy(() -> useCase.read(newCommand())).isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(userPreferenceRepository);
        }

        @Test
        @DisplayName("when the repository throws PersistenceFailedException - then the exception propagates unchanged")
        void whenRepositoryThrowsPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            when(userRepository.requireById(USER_ID)).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(userPreferenceRepository.findDefaultCurrency(USER_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.read(newCommand())).isSameAs(failure);
        }
    }
}
