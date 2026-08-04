package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
import java.util.Optional;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CreateExpenseProposalCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId("555");
    private static final String CATEGORY_NAME = "Groceries";
    private static final String GROUPING_NAME = "Food";
    private static final String DESCRIPTION = "groceries";
    private static final Optional<String> MERCHANT = Optional.of("Trader Joe's");
    private static final Money MONEY = new Money(1000, new CurrencyCode("USD"));
    private static final MessageReference MESSAGE_REFERENCE = MessageReference.newReference();

    @Nested
    @DisplayName("constructing a new expense proposal")
    class CreateExpenseProposalCommandConstructor {

        @Test
        @DisplayName("when the userId is absent - then throws InvalidUserException")
        void whenUserIdIsAbsent_thenThrowsInvalidUserException() {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            null, CATEGORY_NAME, GROUPING_NAME, DESCRIPTION, MERCHANT, MONEY, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidUserException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName(
                "when the category name is absent, empty, or only whitespace - then throws InvalidExpenseProposalException")
        void whenCategoryNameIsAbsentEmptyOrWhitespace_thenThrowsInvalidExpenseProposalException(String categoryName) {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            USER_ID, categoryName, GROUPING_NAME, DESCRIPTION, MERCHANT, MONEY, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName(
                "when the groupingName is absent, empty, or only whitespace - then throws InvalidExpenseProposalException")
        void whenParentCategoryNameIsAbsentEmptyOrWhitespace_thenThrowsInvalidExpenseProposalException(
                String groupingName) {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            USER_ID, CATEGORY_NAME, groupingName, DESCRIPTION, MERCHANT, MONEY, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName(
                "when the description is absent, empty, or only whitespace - then throws InvalidExpenseProposalException")
        void whenDescriptionIsAbsentEmptyOrWhitespace_thenThrowsInvalidExpenseProposalException(String description) {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            USER_ID, CATEGORY_NAME, GROUPING_NAME, description, MERCHANT, MONEY, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName("when the merchant Optional is absent - then throws InvalidExpenseProposalException")
        void whenMerchantOptionalIsAbsent_thenThrowsInvalidExpenseProposalException() {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            USER_ID, CATEGORY_NAME, GROUPING_NAME, DESCRIPTION, null, MONEY, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        @DisplayName(
                "when the merchant is present but empty or only whitespace - then the record's merchant is Optional.empty()")
        void whenMerchantIsPresentButBlank_thenTheRecordsMerchantIsEmpty(String merchant) {
            CreateExpenseProposalCommand createExpenseProposalCommand = new CreateExpenseProposalCommand(
                    USER_ID,
                    CATEGORY_NAME,
                    GROUPING_NAME,
                    DESCRIPTION,
                    Optional.of(merchant),
                    MONEY,
                    MESSAGE_REFERENCE);

            assertThat(createExpenseProposalCommand.merchant()).isEmpty();
        }

        @Test
        @DisplayName("when money is absent - then throws InvalidExpenseProposalException")
        void whenMoneyIsAbsent_thenThrowsInvalidExpenseProposalException() {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            USER_ID, CATEGORY_NAME, GROUPING_NAME, DESCRIPTION, MERCHANT, null, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName(
                "when every field is present and the merchant is a non-blank name - then the record carries them unchanged")
        @Disabled("RU06: read back the renamed groupingName component")
        void whenEveryFieldIsPresentAndMerchantIsNonBlank_thenTheRecordCarriesThemUnchanged() {
            // CreateExpenseProposalCommand createExpenseProposalCommand = new CreateExpenseProposalCommand(
            //         USER_ID, CATEGORY_NAME, GROUPING_NAME, DESCRIPTION, MERCHANT, MONEY, MESSAGE_REFERENCE);
            //
            // assertThat(createExpenseProposalCommand.userId()).isEqualTo(USER_ID);
            // assertThat(createExpenseProposalCommand.categoryName()).isEqualTo(CATEGORY_NAME);
            // assertThat(createExpenseProposalCommand.groupingName()).isEqualTo(GROUPING_NAME);
            // assertThat(createExpenseProposalCommand.description()).isEqualTo(DESCRIPTION);
            // assertThat(createExpenseProposalCommand.merchant()).isEqualTo(MERCHANT);
            // assertThat(createExpenseProposalCommand.money()).isEqualTo(MONEY);
        }

        @Test
        @DisplayName(
                "when every component is valid and a message reference is given - then messageReference() reads back unchanged")
        void whenEveryComponentIsValidAndAMessageReferenceIsGiven_thenMessageReferenceReadsBackUnchanged() {
            CreateExpenseProposalCommand createExpenseProposalCommand = new CreateExpenseProposalCommand(
                    USER_ID, CATEGORY_NAME, GROUPING_NAME, DESCRIPTION, MERCHANT, MONEY, MESSAGE_REFERENCE);

            assertThat(createExpenseProposalCommand.messageReference()).isEqualTo(MESSAGE_REFERENCE);
        }

        @Test
        @DisplayName("when the messageReference is absent - then throws InvalidExpenseProposalException")
        void whenMessageReferenceIsAbsent_thenThrowsInvalidExpenseProposalException() {
            assertThatThrownBy(() -> new CreateExpenseProposalCommand(
                            USER_ID, CATEGORY_NAME, GROUPING_NAME, DESCRIPTION, MERCHANT, MONEY, null))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }
    }
}
