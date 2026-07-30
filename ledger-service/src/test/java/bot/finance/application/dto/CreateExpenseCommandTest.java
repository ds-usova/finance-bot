package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CreateExpenseCommandTest {

    private static final long CATEGORY_ID = 42L;
    private static final String DESCRIPTION = "groceries";
    private static final Optional<String> MERCHANT = Optional.of("Trader Joe's");
    private static final Money MONEY = new Money(1000, new CurrencyCode("USD"));

    @Nested
    @DisplayName("constructing a new expense")
    class CreateExpenseCommandConstructor {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName(
                "when the user external id is absent, empty, or only whitespace - then throws InvalidUserException")
        void whenUserExternalIdIsAbsentEmptyOrWhitespace_thenThrowsInvalidUserException(String userExternalId) {
            assertThatThrownBy(
                            () -> new CreateExpenseCommand(userExternalId, CATEGORY_ID, DESCRIPTION, MERCHANT, MONEY))
                    .isInstanceOf(InvalidUserException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the description is absent, empty, or only whitespace - then throws InvalidExpenseException")
        void whenDescriptionIsAbsentEmptyOrWhitespace_thenThrowsInvalidExpenseException(String description) {
            assertThatThrownBy(() -> new CreateExpenseCommand("555", CATEGORY_ID, description, MERCHANT, MONEY))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the category id is zero or negative - then throws InvalidExpenseException")
        void whenCategoryIdIsZeroOrNegative_thenThrowsInvalidExpenseException(long categoryId) {
            assertThatThrownBy(() -> new CreateExpenseCommand("555", categoryId, DESCRIPTION, MERCHANT, MONEY))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when the merchant Optional is absent - then throws InvalidExpenseException")
        void whenMerchantOptionalIsAbsent_thenThrowsInvalidExpenseException() {
            assertThatThrownBy(() -> new CreateExpenseCommand("555", CATEGORY_ID, DESCRIPTION, null, MONEY))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        @DisplayName(
                "when the merchant is present but empty or only whitespace - then the record's merchant is Optional.empty()")
        void whenMerchantIsPresentButBlank_thenTheRecordsMerchantIsEmpty(String merchant) {
            CreateExpenseCommand createExpenseCommand =
                    new CreateExpenseCommand("555", CATEGORY_ID, DESCRIPTION, Optional.of(merchant), MONEY);

            assertThat(createExpenseCommand.merchant()).isEmpty();
        }

        @Test
        @DisplayName("when money is absent - then throws InvalidExpenseException")
        void whenMoneyIsAbsent_thenThrowsInvalidExpenseException() {
            assertThatThrownBy(() -> new CreateExpenseCommand("555", CATEGORY_ID, DESCRIPTION, MERCHANT, null))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName(
                "when every field is present and the merchant is a non-blank name - then the record carries them unchanged")
        void whenEveryFieldIsPresentAndMerchantIsNonBlank_thenTheRecordCarriesThemUnchanged() {
            CreateExpenseCommand createExpenseCommand =
                    new CreateExpenseCommand("555", CATEGORY_ID, DESCRIPTION, MERCHANT, MONEY);

            assertThat(createExpenseCommand.userExternalId()).isEqualTo("555");
            assertThat(createExpenseCommand.categoryId()).isEqualTo(CATEGORY_ID);
            assertThat(createExpenseCommand.description()).isEqualTo(DESCRIPTION);
            assertThat(createExpenseCommand.merchant()).isEqualTo(MERCHANT);
            assertThat(createExpenseCommand.money()).isEqualTo(MONEY);
        }
    }
}
