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

    private static CreateExpenseProposalToolRequest requestWith(String amount, String currencyCode) {
        return new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", "Corner Shop", amount, currencyCode);
    }

    @Nested
    @DisplayName("mapping a tool request onto the create-expense-proposal command")
    class ToCommand {

        @Test
        @DisplayName("when the request carries every argument and an identity - then returns a command carrying that "
                + "identity, the category names, the description, the merchant, and a Money built from the "
                + "minor units and the currency code")
        void whenRequestCarriesEveryArgumentAndAnIdentity_thenReturnsCommandCarryingThatIdentityAndFields() {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", "Corner Shop", "15.00", "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command)
                    .isEqualTo(new CreateExpenseProposalCommand(
                            USER_ID,
                            "Groceries",
                            "Food",
                            "Milk",
                            Optional.of("Corner Shop"),
                            new Money(1500L, CurrencyCode.of("EUR")),
                            MESSAGE_REFERENCE));
        }

        @Test
        @DisplayName("when the request, an identity and a message reference are valid - then the returned command "
                + "carries that reference")
        void whenRequestIdentityAndReferenceAreValid_thenReturnedCommandCarriesThatReference() {
            CreateExpenseProposalToolRequest request = requestWith("15.00", "EUR");
            MessageReference reference = MessageReference.newReference();

            CreateExpenseProposalCommand command = ExpenseProposalToolUtils.toCommand(request, USER_ID, reference);

            assertThat(command.messageReference()).isEqualTo(reference);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("blankParentCategories")
        @DisplayName("when the request's parentCategory is null, empty, or whitespace only - then throws "
                + "InvalidExpenseProposalException with the message \"expense proposal request has no parent "
                + "category\"")
        void whenParentCategoryIsNullOrBlank_thenThrowsInvalidExpenseProposalException(
                String description, String parentCategory) {
            CreateExpenseProposalToolRequest request = new CreateExpenseProposalToolRequest(
                    "Groceries", parentCategory, "Milk", "Corner Shop", "15.00", "EUR");

            assertThatThrownBy(() -> ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class)
                    .hasMessage("expense proposal request has no parent category");
        }

        static Stream<Arguments> blankParentCategories() {
            return Stream.of(
                    arguments("null parentCategory", null),
                    arguments("empty parentCategory", ""),
                    arguments("blank parentCategory", "   "));
        }

        @Test
        @DisplayName("when the request's parentCategory is absent and its amount is also malformed - then the "
                + "amount's own failure is raised")
        void whenParentCategoryIsAbsentAndAmountIsMalformed_thenThrowsForTheAmount() {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", null, "Milk", "Corner Shop", "twelve", "EUR");

            assertThatThrownBy(() -> ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class)
                    .hasMessage("amount must be digits with an optional dot, like 7200 or 12.50");
        }

        @Test
        @DisplayName("when the request carries a non-blank parentCategory - then the command's parentCategoryName "
                + "is that name")
        void whenParentCategoryIsNonBlank_thenCommandParentCategoryNameIsThatName() {
            CreateExpenseProposalToolRequest request = requestWith("15.00", "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command.parentCategoryName()).isEqualTo("Food");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("blankMerchants")
        @DisplayName("when the request's merchant is null or blank - then the command's merchant is Optional.empty()")
        void whenMerchantIsNullOrBlank_thenCommandMerchantIsEmpty(String description, String merchant) {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", merchant, "15.00", "EUR");

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
            CreateExpenseProposalToolRequest request = requestWith("15.00", "ZZZ");

            assertThatThrownBy(() -> ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidMoneyException.class);
        }

        @Test
        @DisplayName("when the request's amount is absent - then throws InvalidExpenseProposalException, so an "
                + "absent amount is never read as zero")
        void whenAmountIsAbsent_thenThrowsInvalidExpenseProposalException() {
            CreateExpenseProposalToolRequest request = requestWith(null, "EUR");

            assertThatThrownBy(() -> ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class)
                    .hasMessage("expense proposal request has no amount");
        }

        @Test
        @DisplayName("when the request's amount is \"7200\" and its currencyCode is \"HUF\" - then the returned "
                + "command's money carries 720000 minor units and HUF")
        void
                whenAmountIsSevenThousandTwoHundredAndCurrencyIsHuf_thenCommandMoneyCarriesSevenHundredTwentyThousandMinorUnitsAndHuf() {
            CreateExpenseProposalToolRequest request = requestWith("7200", "HUF");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command.money()).isEqualTo(new Money(720000L, CurrencyCode.of("HUF")));
        }

        @Test
        @DisplayName("when the request's amount is \"  12.50  \", with surrounding whitespace - then the returned "
                + "command's money carries 1250 minor units, so the text is stripped before it is read")
        void whenAmountHasSurroundingWhitespace_thenCommandMoneyCarriesMinorUnitsFromTheStrippedText() {
            CreateExpenseProposalToolRequest request = requestWith("  12.50  ", "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command.money()).isEqualTo(new Money(1250L, CurrencyCode.of("EUR")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("malformedAmounts")
        @DisplayName("when the request's amount is a form the description never offered - then throws "
                + "InvalidExpenseProposalException naming the accepted form")
        void whenAmountIsAFormTheDescriptionNeverOffered_thenThrowsInvalidExpenseProposalExceptionNamingTheAcceptedForm(
                String description, String amount) {
            CreateExpenseProposalToolRequest request = requestWith(amount, "EUR");

            assertThatThrownBy(() -> ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class)
                    .hasMessage("amount must be digits with an optional dot, like 7200 or 12.50");
        }

        static Stream<Arguments> malformedAmounts() {
            return Stream.of(
                    arguments("comma decimal", "12,50"),
                    arguments("grouped digits", "7,200"),
                    arguments("negative sign", "-5.00"),
                    arguments("exponent", "1e3"),
                    arguments("currency symbol", "€12"),
                    arguments("trailing dot with no decimals", "12."),
                    arguments("not a number", "twelve"),
                    arguments("empty string", ""),
                    arguments("blank string", "   "),
                    arguments("nineteen digits", "1234567890123456789"),
                    arguments("five decimals", "1.23456"));
        }

        @Test
        @DisplayName("when the request's amount is \"0\" and its currencyCode is \"EUR\" - then the returned "
                + "command's money carries zero minor units")
        void whenAmountIsZeroAndCurrencyIsEur_thenCommandMoneyCarriesZeroMinorUnits() {
            CreateExpenseProposalToolRequest request = requestWith("0", "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolUtils.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command.money().minorUnits()).isZero();
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
                        + "merchant, the amount as text, the currency code and the created-at instant")
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
                            1L, "Groceries", "Milk", "Corner Shop", "15.00", "EUR", createdAt));
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
