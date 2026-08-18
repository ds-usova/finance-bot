package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.common.fixtures.SpendingFactFixtures;
import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SpendingRowTest {

    @Nested
    @DisplayName("constructing a SpendingRow")
    class CompactConstructor {

        static Stream<Arguments> invalidIdScenarios() {
            SpendingRow valid = SpendingFactFixtures.spendingRow();
            return Stream.of(
                    Arguments.of(Named.of("zero expenseId", (ThrowingCallable) () -> new SpendingRow(
                            0L,
                            valid.userId(),
                            valid.incomingMessageId(),
                            valid.description(),
                            valid.merchant(),
                            valid.amount(),
                            valid.currencyCode(),
                            valid.category(),
                            valid.grouping()))),
                    Arguments.of(Named.of("negative expenseId", (ThrowingCallable) () -> new SpendingRow(
                            -1L,
                            valid.userId(),
                            valid.incomingMessageId(),
                            valid.description(),
                            valid.merchant(),
                            valid.amount(),
                            valid.currencyCode(),
                            valid.category(),
                            valid.grouping()))),
                    Arguments.of(Named.of("zero userId", (ThrowingCallable) () -> new SpendingRow(
                            valid.expenseId(),
                            0L,
                            valid.incomingMessageId(),
                            valid.description(),
                            valid.merchant(),
                            valid.amount(),
                            valid.currencyCode(),
                            valid.category(),
                            valid.grouping()))),
                    Arguments.of(Named.of("negative userId", (ThrowingCallable) () -> new SpendingRow(
                            valid.expenseId(),
                            -1L,
                            valid.incomingMessageId(),
                            valid.description(),
                            valid.merchant(),
                            valid.amount(),
                            valid.currencyCode(),
                            valid.category(),
                            valid.grouping()))));
        }

        @ParameterizedTest
        @MethodSource("invalidIdScenarios")
        @DisplayName("when expenseId or userId is zero or below - then throws InvalidValueException")
        void whenExpenseIdOrUserIdIsZeroOrBelow_thenThrowsInvalidValueException(ThrowingCallable constructor) {
            assertThatThrownBy(constructor).isInstanceOf(InvalidValueException.class);
        }

        static Stream<Arguments> invalidDescriptionOrAmountScenarios() {
            SpendingRow valid = SpendingFactFixtures.spendingRow();
            return Stream.of(
                    Arguments.of(Named.of("null description", (ThrowingCallable) () -> new SpendingRow(
                            valid.expenseId(),
                            valid.userId(),
                            valid.incomingMessageId(),
                            null,
                            valid.merchant(),
                            valid.amount(),
                            valid.currencyCode(),
                            valid.category(),
                            valid.grouping()))),
                    Arguments.of(Named.of("blank description", (ThrowingCallable) () -> new SpendingRow(
                            valid.expenseId(),
                            valid.userId(),
                            valid.incomingMessageId(),
                            "   ",
                            valid.merchant(),
                            valid.amount(),
                            valid.currencyCode(),
                            valid.category(),
                            valid.grouping()))),
                    Arguments.of(Named.of("null amount", (ThrowingCallable) () -> new SpendingRow(
                            valid.expenseId(),
                            valid.userId(),
                            valid.incomingMessageId(),
                            valid.description(),
                            valid.merchant(),
                            null,
                            valid.currencyCode(),
                            valid.category(),
                            valid.grouping()))),
                    Arguments.of(Named.of("blank amount", (ThrowingCallable) () -> new SpendingRow(
                            valid.expenseId(),
                            valid.userId(),
                            valid.incomingMessageId(),
                            valid.description(),
                            valid.merchant(),
                            "   ",
                            valid.currencyCode(),
                            valid.category(),
                            valid.grouping()))));
        }

        @ParameterizedTest
        @MethodSource("invalidDescriptionOrAmountScenarios")
        @DisplayName("when description or amount is null or blank - then throws InvalidValueException")
        void whenDescriptionOrAmountIsNullOrBlank_thenThrowsInvalidValueException(ThrowingCallable constructor) {
            assertThatThrownBy(constructor).isInstanceOf(InvalidValueException.class);
        }

        static Stream<Arguments> invalidRequiredComponentScenarios() {
            SpendingRow valid = SpendingFactFixtures.spendingRow();
            return Stream.of(
                    Arguments.of(Named.of("null currencyCode", (ThrowingCallable) () -> new SpendingRow(
                            valid.expenseId(),
                            valid.userId(),
                            valid.incomingMessageId(),
                            valid.description(),
                            valid.merchant(),
                            valid.amount(),
                            null,
                            valid.category(),
                            valid.grouping()))),
                    Arguments.of(Named.of("null category", (ThrowingCallable) () -> new SpendingRow(
                            valid.expenseId(),
                            valid.userId(),
                            valid.incomingMessageId(),
                            valid.description(),
                            valid.merchant(),
                            valid.amount(),
                            valid.currencyCode(),
                            null,
                            valid.grouping()))),
                    Arguments.of(Named.of("null incomingMessageId Optional", (ThrowingCallable) () -> new SpendingRow(
                            valid.expenseId(),
                            valid.userId(),
                            null,
                            valid.description(),
                            valid.merchant(),
                            valid.amount(),
                            valid.currencyCode(),
                            valid.category(),
                            valid.grouping()))),
                    Arguments.of(Named.of("null merchant Optional", (ThrowingCallable) () -> new SpendingRow(
                            valid.expenseId(),
                            valid.userId(),
                            valid.incomingMessageId(),
                            valid.description(),
                            null,
                            valid.amount(),
                            valid.currencyCode(),
                            valid.category(),
                            valid.grouping()))),
                    Arguments.of(Named.of("null grouping Optional", (ThrowingCallable) () -> new SpendingRow(
                            valid.expenseId(),
                            valid.userId(),
                            valid.incomingMessageId(),
                            valid.description(),
                            valid.merchant(),
                            valid.amount(),
                            valid.currencyCode(),
                            valid.category(),
                            null))));
        }

        @ParameterizedTest
        @MethodSource("invalidRequiredComponentScenarios")
        @DisplayName(
                "when currencyCode, category, or an Optional component is null - then throws InvalidValueException")
        void whenCurrencyCodeCategoryOrAnOptionalComponentIsNull_thenThrowsInvalidValueException(
                ThrowingCallable constructor) {
            assertThatThrownBy(constructor).isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when grouping and merchant are empty - then it is accepted and both read back empty")
        void whenGroupingAndMerchantAreEmpty_thenItIsAcceptedAndBothReadBackEmpty() {
            SpendingRow valid = SpendingFactFixtures.spendingRow();

            SpendingRow row = new SpendingRow(
                    valid.expenseId(),
                    valid.userId(),
                    valid.incomingMessageId(),
                    valid.description(),
                    Optional.empty(),
                    valid.amount(),
                    valid.currencyCode(),
                    valid.category(),
                    Optional.empty());

            assertThat(row.merchant()).isEmpty();
            assertThat(row.grouping()).isEmpty();
        }
    }

    @Nested
    @DisplayName("reading the message identity a SpendingRow carries")
    class MessageIdentityMethod {

        @Test
        @DisplayName("when the row carries an incoming message id - then it carries the row's userId and that id")
        void whenRowCarriesIncomingMessageId_thenIdentityCarriesUserIdAndMessageId() {
            SpendingRow row = SpendingFactFixtures.spendingRow();

            Optional<MessageIdentity> identity = row.messageIdentity();

            assertThat(identity)
                    .contains(new MessageIdentity(
                            row.userId(), row.incomingMessageId().get()));
        }

        @Test
        @DisplayName("when the row has no incoming message id - then it is empty")
        void whenRowHasNoIncomingMessageId_thenItIsEmpty() {
            SpendingRow row =
                    SpendingFactFixtures.spendingRow(SpendingFactFixtures.DEFAULT_EXPENSE_ID, Optional.empty());

            assertThat(row.messageIdentity()).isEmpty();
        }
    }
}
