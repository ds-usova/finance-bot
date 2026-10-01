package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.Preferences;
import bot.finance.application.dto.ReplacePreferencesCommand;
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

class ReplacePreferencesUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;

    private UserRepository userRepository;
    private UserPreferenceRepository userPreferenceRepository;
    private ReplacePreferencesUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userPreferenceRepository = mock(UserPreferenceRepository.class);
        useCase = new ReplacePreferencesUseCase(userRepository, userPreferenceRepository);
    }

    private ReplacePreferencesCommand newCommand(CurrencyCode currencyCode) {
        return new ReplacePreferencesCommand(new AuthenticatedUserId(USER_ID), currencyCode);
    }

    @Nested
    @DisplayName("replacing preferences")
    class Replace {

        @Test
        @DisplayName("when the caller has no preference row - then the write carries the command's currency, and "
                + "so does the answer")
        void whenCallerHasNoPreferenceRowAndCommandCarriesCurrency_thenRepositoryWritesItAndAnswerCarriesIt() {
            when(userRepository.requireById(USER_ID)).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            CurrencyCode currencyCode = CurrencyCode.of("EUR");

            Preferences result = useCase.replace(newCommand(currencyCode));

            verify(userPreferenceRepository).replaceDefaultCurrency(USER_ID, currencyCode);
            assertThat(result.defaultCurrency()).contains(currencyCode);
        }

        @Test
        @DisplayName("when the row holds a currency and the command carries a different one - then that one is "
                + "written and answered")
        void whenCallerRowHoldsCurrencyAndCommandCarriesDifferentOne_thenRepositoryWritesNewOneAndAnswerCarriesIt() {
            when(userRepository.requireById(USER_ID)).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            when(userPreferenceRepository.findDefaultCurrency(USER_ID)).thenReturn(Optional.of(CurrencyCode.of("EUR")));
            CurrencyCode newCurrency = CurrencyCode.of("USD");

            Preferences result = useCase.replace(newCommand(newCurrency));

            verify(userPreferenceRepository).replaceDefaultCurrency(USER_ID, newCurrency);
            assertThat(result.defaultCurrency()).contains(newCurrency);
        }

        @Test
        @DisplayName("when the user store does not hold the caller - then EntityNotFoundException propagates and "
                + "nothing is written")
        void whenUserStoreDoesNotHoldCaller_thenEntityNotFoundExceptionPropagatesAndNothingIsWritten() {
            when(userRepository.requireById(USER_ID))
                    .thenThrow(new EntityNotFoundException("user", "no user stored under id " + USER_ID));

            assertThatThrownBy(() -> useCase.replace(newCommand(CurrencyCode.of("EUR"))))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(userPreferenceRepository);
        }

        @Test
        @DisplayName("when the repository throws PersistenceFailedException on the write - then the exception "
                + "propagates unchanged")
        void whenRepositoryThrowsPersistenceFailedExceptionOnWrite_thenExceptionPropagatesUnchanged() {
            when(userRepository.requireById(USER_ID)).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            PersistenceFailedException failure = new PersistenceFailedException("write failed", new RuntimeException());
            CurrencyCode currencyCode = CurrencyCode.of("EUR");
            doThrow(failure).when(userPreferenceRepository).replaceDefaultCurrency(USER_ID, currencyCode);

            assertThatThrownBy(() -> useCase.replace(newCommand(currencyCode))).isSameAs(failure);
        }
    }
}
