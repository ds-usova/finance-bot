package bot.finance.adapter.mcp;

import static bot.finance.common.fixtures.IncomingMessages.newIncomingMessageId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.InvalidMoneyException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
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

class ExpenseProposalToolMapperTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId("user-1");
    private static final IncomingMessageId MESSAGE_REFERENCE = newIncomingMessageId();

    private static CreateExpenseProposalToolRequest requestWith(String amount, String currencyCode) {
        return new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", "Corner Shop", amount, currencyCode);
    }

    @Nested
    @DisplayName("mapping a tool request onto the create-expense-proposal command")
    class ToCommand {

        @Test
        @DisplayName("when the request carries every argument and an identity - then returns the command those "
                + "arguments map onto")
        void whenRequestCarriesEveryArgumentAndAnIdentity_thenReturnsTheCommandTheyMapOnto() {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", "Corner Shop", "15.00", "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolMapper.toCommand(request, USER_ID, MESSAGE_REFERENCE);

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
            IncomingMessageId reference = newIncomingMessageId();

            CreateExpenseProposalCommand command = ExpenseProposalToolMapper.toCommand(request, USER_ID, reference);

            assertThat(command.incomingMessageId()).isEqualTo(reference);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("blankGroupings")
        @DisplayName("when the request's grouping is null, empty, or whitespace only - then throws "
                + "InvalidExpenseProposalException")
        void whenGroupingIsNullOrBlank_thenThrowsInvalidExpenseProposalException(String description, String grouping) {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", grouping, "Milk", "Corner Shop", "15.00", "EUR");

            assertThatThrownBy(() -> ExpenseProposalToolMapper.toCommand(request, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class)
                    .hasMessage("expense proposal request has no grouping");
        }

        static Stream<Arguments> blankGroupings() {
            return Stream.of(
                    arguments("null grouping", null),
                    arguments("empty grouping", ""),
                    arguments("blank grouping", "   "));
        }

        @Test
        @DisplayName("when the request's grouping is absent and its amount is also malformed - then the "
                + "amount's own failure is raised")
        void whenGroupingIsAbsentAndAmountIsMalformed_thenThrowsForTheAmount() {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", null, "Milk", "Corner Shop", "twelve", "EUR");

            assertThatThrownBy(() -> ExpenseProposalToolMapper.toCommand(request, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class)
                    .hasMessage("amount must be digits with an optional dot, like 7200 or 12.50");
        }

        @Test
        @DisplayName("when the request carries a non-blank grouping - then the command's groupingName is that name")
        void whenGroupingIsNonBlank_thenCommandGroupingNameIsThatName() {
            CreateExpenseProposalToolRequest request = requestWith("15.00", "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolMapper.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command.groupingName()).isEqualTo("Food");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("blankMerchants")
        @DisplayName("when the request's merchant is null or blank - then the command's merchant is Optional.empty()")
        void whenMerchantIsNullOrBlank_thenCommandMerchantIsEmpty(String description, String merchant) {
            CreateExpenseProposalToolRequest request =
                    new CreateExpenseProposalToolRequest("Groceries", "Food", "Milk", merchant, "15.00", "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolMapper.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command.merchant()).isEmpty();
        }

        static Stream<Arguments> blankMerchants() {
            return Stream.of(arguments("null merchant", null), arguments("blank merchant", "   "));
        }

        @Test
        @DisplayName("when the request's currencyCode is not an ISO 4217 code - then throws InvalidMoneyException")
        void whenCurrencyCodeIsNotAnIso4217Code_thenThrowsInvalidMoneyException() {
            CreateExpenseProposalToolRequest request = requestWith("15.00", "ZZZ");

            assertThatThrownBy(() -> ExpenseProposalToolMapper.toCommand(request, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidMoneyException.class);
        }

        @Test
        @DisplayName("when the request's amount is absent - then throws InvalidExpenseProposalException")
        void whenAmountIsAbsent_thenThrowsInvalidExpenseProposalException() {
            CreateExpenseProposalToolRequest request = requestWith(null, "EUR");

            assertThatThrownBy(() -> ExpenseProposalToolMapper.toCommand(request, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class)
                    .hasMessage("expense proposal request has no amount");
        }

        @Test
        @DisplayName("when the request's amount is \"7200\" in \"HUF\" - then the command's money carries 720000 "
                + "minor units")
        void whenAmountIsSevenThousandTwoHundredInHuf_thenCommandMoneyCarriesTheScaledMinorUnits() {
            CreateExpenseProposalToolRequest request = requestWith("7200", "HUF");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolMapper.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command.money()).isEqualTo(new Money(720000L, CurrencyCode.of("HUF")));
        }

        @Test
        @DisplayName("when the request's amount has surrounding whitespace - then the command's money carries the "
                + "stripped text's minor units")
        void whenAmountHasSurroundingWhitespace_thenCommandMoneyCarriesMinorUnitsFromTheStrippedText() {
            CreateExpenseProposalToolRequest request = requestWith("  12.50  ", "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolMapper.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command.money()).isEqualTo(new Money(1250L, CurrencyCode.of("EUR")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("malformedAmounts")
        @DisplayName(
                "when the request's amount is a form never offered - then throws " + "InvalidExpenseProposalException")
        void whenAmountIsAFormNeverOffered_thenThrowsInvalidExpenseProposalException(
                String description, String amount) {
            CreateExpenseProposalToolRequest request = requestWith(amount, "EUR");

            assertThatThrownBy(() -> ExpenseProposalToolMapper.toCommand(request, USER_ID, MESSAGE_REFERENCE))
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
        @DisplayName("when the request's amount is \"0\" - then the command's money carries zero minor units")
        void whenAmountIsZero_thenCommandMoneyCarriesZeroMinorUnits() {
            CreateExpenseProposalToolRequest request = requestWith("0", "EUR");

            CreateExpenseProposalCommand command =
                    ExpenseProposalToolMapper.toCommand(request, USER_ID, MESSAGE_REFERENCE);

            assertThat(command.money().minorUnits()).isZero();
        }

        @Test
        @DisplayName("when the request is absent - then throws InvalidExpenseProposalException")
        void whenRequestIsAbsent_thenThrowsInvalidExpenseProposalException() {
            assertThatThrownBy(() -> ExpenseProposalToolMapper.toCommand(null, USER_ID, MESSAGE_REFERENCE))
                    .isInstanceOf(InvalidExpenseProposalException.class);
        }
    }

    @Nested
    @DisplayName("mapping a stored expense proposal onto the tool's response")
    class ToResponse {

        @Test
        @DisplayName("when a stored proposal carries a merchant and the category it was filed under - then returns "
                + "the response they map onto")
        void whenStoredProposalCarriesMerchantAndCategoryName_thenReturnsTheResponseTheyMapOnto() {
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

            CreateExpenseProposalToolResponse response = ExpenseProposalToolMapper.toResponse(proposal, "Groceries");

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

            CreateExpenseProposalToolResponse response = ExpenseProposalToolMapper.toResponse(proposal, "Groceries");

            assertThat(response.merchant()).isNull();
        }
    }
}
