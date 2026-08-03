package bot.finance.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.InvalidMoneyException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExpenseProposalToolUtilsTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId("user-1");
    private static final MessageReference MESSAGE_REFERENCE = MessageReference.newReference();

    @Nested
    @DisplayName("mapping a tool request onto the create-expense-proposal command")
    class ToCommand {

        @Test
        @DisplayName("when the request carries every argument and an identity - then returns a command carrying that "
                + "identity, the category names, the description, the merchant, and a Money built from the "
                + "minor units and the currency code")
        void whenRequestCarriesEveryArgumentAndAnIdentity_thenReturnsCommandCarryingThatIdentityAndFields() {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", "Corner Shop", 1500L, "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command)
                    .isEqualTo(new CreateExpenseProposalCommand(
                            USER_ID,
                            "Groceries",
                            Optional.of("Food"),
                            "Milk",
                            Optional.of("Corner Shop"),
                            new Money(1500L, CurrencyCode.of("EUR")),
                            MESSAGE_REFERENCE));
        }

        @Test
        @DisplayName("when the request, an identity and a message reference are valid - then the returned command "
                + "carries that reference")
        void whenRequestIdentityAndReferenceAreValid_thenReturnedCommandCarriesThatReference() {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", "Corner Shop", 1500L, "EUR");
            MessageReference reference = MessageReference.newReference();

            CreateExpenseProposalCommand command = ExpenseProposalToolUtils.toCommand(request, USER_ID, reference);

            assertThat(command.messageReference()).isEqualTo(reference);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("blankParentCategories")
        @DisplayName(
                "when the request's parentCategory is null or blank - then the command's parentCategoryName is Optional.empty()")
        void whenParentCategoryIsNullOrBlank_thenCommandParentCategoryNameIsEmpty(
                String description, String parentCategory) {
            CreateExpenseProposalToolRequest request = new CreateExpenseProposalToolRequest(
                    "Groceries", parentCategory, "Milk", "Corner Shop", 1500L, "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command.parentCategoryName()).isEmpty();
        }

        static Stream<Arguments> blankParentCategories() {
            return Stream.of(arguments("null parentCategory", null), arguments("blank parentCategory", "   "));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("blankMerchants")
        @DisplayName("when the request's merchant is null or blank - then the command's merchant is Optional.empty()")
        void whenMerchantIsNullOrBlank_thenCommandMerchantIsEmpty(String description, String merchant) {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", merchant, 1500L, "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command.merchant()).isEmpty();
        }

        static Stream<Arguments> blankMerchants() {
            return Stream.of(arguments("null merchant", null), arguments("blank merchant", "   "));
        }

        @Test
        @DisplayName("when the request's currencyCode is not an ISO 4217 code - then throws InvalidMoneyException")
        void whenCurrencyCodeIsNotAnIso4217Code_thenThrowsInvalidMoneyException() {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", "Corner Shop", 1500L, "ZZZ");

            assertThatThrownBy(() -> ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidMoneyException.class);
        }

        @Test
        @DisplayName(
                "when the request's amountMinorUnits is absent - then throws InvalidExpenseProposalException, so an "
                        + "absent amount is never read as zero")
        void whenAmountMinorUnitsIsAbsent_thenThrowsInvalidExpenseProposalException() {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", "Corner Shop", null, "EUR");

            assertThatThrownBy(() -> ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName("when the request's amountMinorUnits is zero - then returns a command whose Money carries zero "
                + "minor units")
        void whenAmountMinorUnitsIsZero_thenReturnsCommandWithZeroMinorUnitsMoney() {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", "Corner Shop", 0L, "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command.money().minorUnits()).isZero();
        }

        @Test
        @DisplayName("when the request's amountMinorUnits is negative - then throws InvalidMoneyException")
        void whenAmountMinorUnitsIsNegative_thenThrowsInvalidMoneyException() {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", "Corner Shop", -1L, "EUR");

            assertThatThrownBy(() -> ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidMoneyException.class);
        }

        @Test
        @DisplayName("when the request is absent - then throws InvalidExpenseProposalException")
        void whenRequestIsAbsent_thenThrowsInvalidExpenseProposalException() {
            assertThatThrownBy(() -> ExpenseProposalToolUtils.toCommand(null, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }
    }

    @Nested
    @DisplayName("mapping a stored expense proposal onto the tool's response")
    class ToResponse {

        @Test
        @DisplayName(
                "when a stored proposal carries a merchant and the category name it was filed under - then returns "
                        + "a response carrying the proposal's id, that category name, the description, the "
                        + "merchant, the minor units, the currency code and the created-at instant")
        void whenStoredProposalCarriesMerchantAndCategoryName_thenReturnsResponseCarryingThoseFields() {
            Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
            ExpenseProposal proposal = ExpenseProposal.stored(
                    1L,
                    10L,
                    20L,
                    "Milk",
                    Optional.of("Corner Shop"),
                    new Money(1500L, CurrencyCode.of("EUR")),
                    MESSAGE_REFERENCE,
                    createdAt,
                    createdAt);

            CreateExpenseProposalToolResponse response = ExpenseProposalToolUtils.toResponse(proposal, "Groceries");

            assertThat(response)
                    .isEqualTo(new CreateExpenseProposalToolResponse(
                            1L, "Groceries", "Milk", "Corner Shop", 1500L, "EUR", createdAt));
        }

        @Test
        @DisplayName("when a stored proposal has no merchant - then the response's merchant is null")
        void whenStoredProposalHasNoMerchant_thenResponseMerchantIsNull() {
            Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
            ExpenseProposal proposal = ExpenseProposal.stored(
                    1L,
                    10L,
                    20L,
                    "Milk",
                    Optional.empty(),
                    new Money(1500L, CurrencyCode.of("EUR")),
                    MESSAGE_REFERENCE,
                    createdAt,
                    createdAt);

            CreateExpenseProposalToolResponse response = ExpenseProposalToolUtils.toResponse(proposal, "Groceries");

            assertThat(response.merchant()).isNull();
        }
    }
}
