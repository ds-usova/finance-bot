package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CreateExpenseProposalCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId("555");
    private static final String CATEGORY_NAME = "Groceries";
    private static final Optional<String> PARENT_CATEGORY_NAME = Optional.empty();
    private static final String DESCRIPTION = "groceries";
    private static final Optional<String> MERCHANT = Optional.of("Trader Joe's");
    private static final Money MONEY = new Money(1000, new CurrencyCode("USD"));

    @Nested
    @DisplayName("constructing a new expense proposal")
    class CreateExpenseProposalCommandConstructor {

        @Test
        @DisplayName("when the userId is absent - then throws InvalidUserException")
        void whenUserIdIsAbsent_thenThrowsInvalidUserException() {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            null, CATEGORY_NAME, PARENT_CATEGORY_NAME, DESCRIPTION, MERCHANT, MONEY))
                    .isInstanceOf(InvalidUserException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName(
                "when the category name is absent, empty, or only whitespace - then throws InvalidExpenseProposalException")
        void whenCategoryNameIsAbsentEmptyOrWhitespace_thenThrowsInvalidExpenseProposalException(
                String categoryName) {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            USER_ID, categoryName, PARENT_CATEGORY_NAME, DESCRIPTION, MERCHANT, MONEY))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName("when the parentCategoryName Optional is absent - then throws InvalidExpenseProposalException")
        void whenParentCategoryNameOptionalIsAbsent_thenThrowsInvalidExpenseProposalException() {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            USER_ID, CATEGORY_NAME, null, DESCRIPTION, MERCHANT, MONEY))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        @DisplayName(
                "when the parentCategoryName is present but empty or only whitespace - then the record's parentCategoryName is Optional.empty()")
        void whenParentCategoryNameIsPresentButBlank_thenTheRecordsParentCategoryNameIsEmpty(
                String parentCategoryName) {
            CreateExpenseProposalCommand createExpenseProposalCommand = new CreateExpenseProposalCommand(
                    USER_ID,
                    CATEGORY_NAME,
                    Optional.of(parentCategoryName),
                    DESCRIPTION,
                    MERCHANT,
                    MONEY);

            assertThat(createExpenseProposalCommand.parentCategoryName()).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName(
                "when the description is absent, empty, or only whitespace - then throws InvalidExpenseProposalException")
        void whenDescriptionIsAbsentEmptyOrWhitespace_thenThrowsInvalidExpenseProposalException(String description) {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            USER_ID, CATEGORY_NAME, PARENT_CATEGORY_NAME, description, MERCHANT, MONEY))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName("when the merchant Optional is absent - then throws InvalidExpenseProposalException")
        void whenMerchantOptionalIsAbsent_thenThrowsInvalidExpenseProposalException() {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            USER_ID, CATEGORY_NAME, PARENT_CATEGORY_NAME, DESCRIPTION, null, MONEY))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        @DisplayName(
                "when the merchant is present but empty or only whitespace - then the record's merchant is Optional.empty()")
        void whenMerchantIsPresentButBlank_thenTheRecordsMerchantIsEmpty(String merchant) {
            CreateExpenseProposalCommand createExpenseProposalCommand = new CreateExpenseProposalCommand(
                    USER_ID, CATEGORY_NAME, PARENT_CATEGORY_NAME, DESCRIPTION, Optional.of(merchant), MONEY);

            assertThat(createExpenseProposalCommand.merchant()).isEmpty();
        }

        @Test
        @DisplayName("when money is absent - then throws InvalidExpenseProposalException")
        void whenMoneyIsAbsent_thenThrowsInvalidExpenseProposalException() {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            USER_ID, CATEGORY_NAME, PARENT_CATEGORY_NAME, DESCRIPTION, MERCHANT, null))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName(
                "when every field is present and the merchant is a non-blank name - then the record carries them unchanged")
        void whenEveryFieldIsPresentAndMerchantIsNonBlank_thenTheRecordCarriesThemUnchanged() {
            CreateExpenseProposalCommand createExpenseProposalCommand = new CreateExpenseProposalCommand(
                    USER_ID, CATEGORY_NAME, PARENT_CATEGORY_NAME, DESCRIPTION, MERCHANT, MONEY);

            assertThat(createExpenseProposalCommand.userId()).isEqualTo(USER_ID);
            assertThat(createExpenseProposalCommand.categoryName()).isEqualTo(CATEGORY_NAME);
            assertThat(createExpenseProposalCommand.parentCategoryName()).isEqualTo(PARENT_CATEGORY_NAME);
            assertThat(createExpenseProposalCommand.description()).isEqualTo(DESCRIPTION);
            assertThat(createExpenseProposalCommand.merchant()).isEqualTo(MERCHANT);
            assertThat(createExpenseProposalCommand.money()).isEqualTo(MONEY);
        }
    }
}
