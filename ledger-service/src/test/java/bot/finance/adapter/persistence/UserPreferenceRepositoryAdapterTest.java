package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.UserPreferenceRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.CurrencyCode;
import java.util.Optional;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@PersistenceAdapterTest
@Import({UserPreferenceRepositoryAdapter.class, Slf4jLoggerFactory.class})
class UserPreferenceRepositoryAdapterTest {

    @Autowired
    private UserPreferenceRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("finding the default currency")
    class FindDefaultCurrency {

        @Test
        @DisplayName("when the user's preference row holds EUR - then EUR is answered")
        void whenPreferenceRowHoldsEur_thenEurAnswered() {
            long userId = storedUserId("user-preference-find-eur");
            storedPreference(userId, "EUR");

            Optional<CurrencyCode> defaultCurrency = adapter.findDefaultCurrency(userId);

            assertThat(defaultCurrency).contains(new CurrencyCode("EUR"));
        }

        @Test
        @DisplayName("when the user has no preference row - then nothing is answered")
        void whenUserHasNoPreferenceRow_thenNothingAnswered() {
            long userId = storedUserId("user-preference-find-none");

            Optional<CurrencyCode> defaultCurrency = adapter.findDefaultCurrency(userId);

            assertThat(defaultCurrency).isEmpty();
        }

        @Test
        @DisplayName("when the row holds eur written directly - then a CurrencyCode of EUR is answered")
        void whenRowHoldsLowercaseEur_thenCurrencyCodeOfEurAnswered() {
            long userId = storedUserId("user-preference-find-lowercase");
            storedPreference(userId, "eur");

            Optional<CurrencyCode> defaultCurrency = adapter.findDefaultCurrency(userId);

            assertThat(defaultCurrency).contains(new CurrencyCode("EUR"));
        }

        @Test
        @Disabled("R01: the reproduction, enabled by the red step")
        @DisplayName("when the row holds a code the JDK does not recognise - then nothing is answered")
        void whenRowHoldsUnrecognisedCode_thenNothingAnswered() {
            long userId = storedUserId("user-preference-find-unrecognised");
            storedPreference(userId, "ZZZ");

            Optional<CurrencyCode> defaultCurrency = adapter.findDefaultCurrency(userId);

            assertThat(defaultCurrency).isEmpty();
        }
    }

    @Nested
    @DisplayName("replacing the default currency")
    class ReplaceDefaultCurrency {

        @Test
        @DisplayName("when the user has no preference row - then one row holds the given currency for that user")
        void whenUserHasNoPreferenceRow_thenOneRowHoldsGivenCurrency() {
            long userId = storedUserId("user-preference-replace-insert");

            adapter.replaceDefaultCurrency(userId, new CurrencyCode("EUR"));

            assertThat(storedDefaultCurrencyCode(userId)).contains("EUR");
        }

        @Test
        @DisplayName("when the user's row holds EUR - then that user still has exactly one row, and it holds USD")
        void whenUsersRowHoldsEur_thenExactlyOneRowHoldsUsd() {
            long userId = storedUserId("user-preference-replace-update");
            storedPreference(userId, "EUR");

            adapter.replaceDefaultCurrency(userId, new CurrencyCode("USD"));

            assertThat(storedDefaultCurrencyCode(userId)).contains("USD");
        }

        @Test
        @DisplayName("when the user id names no stored user - then throws PersistenceFailedException")
        void whenUserIdNamesNoStoredUser_thenThrowsPersistenceFailedException() {
            long unknownUserId = 999_999_999L;

            assertThatThrownBy(() -> adapter.replaceDefaultCurrency(unknownUserId, new CurrencyCode("EUR")))
                    .isInstanceOf(PersistenceFailedException.class);
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private void storedPreference(long userId, String defaultCurrencyCode) {
        UserPreferenceRowUtils.storedPreference(jdbcAggregateTemplate, userId, defaultCurrencyCode);
    }

    private Optional<String> storedDefaultCurrencyCode(long userId) {
        return UserPreferenceRowUtils.storedDefaultCurrencyCode(jdbcAggregateTemplate, userId);
    }
}
