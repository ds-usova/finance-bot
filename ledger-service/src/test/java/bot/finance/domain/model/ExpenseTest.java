package bot.finance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ExpenseTest {

    private static final Money MONEY = new Money(1500L, CurrencyCode.of("USD"));

    @Nested
    @DisplayName("creating a new expense")
    class NewExpenseFactory {

        @Test
        @DisplayName(
                "when a user id, a category id, a description, a merchant, a money and an instant are given - then returns an expense carrying all of them, with no database id, and with both timestamps equal to that instant")
        void whenAllFieldsAreGiven_thenReturnsExpenseCarryingThemWithNoDatabaseIdAndBothTimestampsEqualToInstant() {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            Expense expense = Expense.newExpense(1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, now);

            assertThat(expense.id()).isEmpty();
            assertThat(expense.userId()).isEqualTo(1L);
            assertThat(expense.categoryId()).isEqualTo(2L);
            assertThat(expense.description()).isEqualTo("Coffee");
            assertThat(expense.merchant()).contains("Blue Bottle");
            assertThat(expense.money()).isEqualTo(MONEY);
            assertThat(expense.createdAt()).isEqualTo(now);
            assertThat(expense.updatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("when the merchant is absent - then returns an expense whose merchant is empty")
        void whenMerchantIsAbsent_thenReturnsExpenseWhoseMerchantIsEmpty() {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            Expense expense = Expense.newExpense(1L, 2L, "Coffee", Optional.empty(), MONEY, now);

            assertThat(expense.merchant()).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the description is absent, empty, or only whitespace - then throws InvalidExpenseException")
        void whenDescriptionIsAbsentEmptyOrWhitespace_thenThrowsInvalidExpenseException(String description) {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            assertThatThrownBy(() -> Expense.newExpense(1L, 2L, description, Optional.of("Blue Bottle"), MONEY, now))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when money is absent - then throws InvalidExpenseException")
        void whenMoneyIsAbsent_thenThrowsInvalidExpenseException() {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            assertThatThrownBy(() -> Expense.newExpense(1L, 2L, "Coffee", Optional.of("Blue Bottle"), null, now))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the user id is zero or negative - then throws InvalidExpenseException")
        void whenUserIdIsZeroOrNegative_thenThrowsInvalidExpenseException(long userId) {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            assertThatThrownBy(() -> Expense.newExpense(userId, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, now))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the category id is zero or negative - then throws InvalidExpenseException")
        void whenCategoryIdIsZeroOrNegative_thenThrowsInvalidExpenseException(long categoryId) {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            assertThatThrownBy(
                            () -> Expense.newExpense(1L, categoryId, "Coffee", Optional.of("Blue Bottle"), MONEY, now))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when the merchant Optional is absent - then throws InvalidExpenseException")
        void whenMerchantOptionalIsAbsent_thenThrowsInvalidExpenseException() {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            assertThatThrownBy(() -> Expense.newExpense(1L, 2L, "Coffee", (Optional<String>) null, MONEY, now))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when the instant is absent - then throws InvalidExpenseException")
        void whenInstantIsAbsent_thenThrowsInvalidExpenseException() {
            assertThatThrownBy(() -> Expense.newExpense(1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, null))
                    .isInstanceOf(InvalidExpenseException.class);
        }
    }

    @Nested
    @DisplayName("reconstituting a stored expense")
    class StoredFactory {

        @Test
        @DisplayName(
                "when a database id and every other field are given, with a created_at earlier than its updated_at - then returns an expense carrying all of them, each timestamp unchanged")
        void whenDatabaseIdAndEveryOtherFieldAreGiven_thenReturnsExpenseCarryingAllWithTimestampsUnchanged() {
            Instant createdAt = Instant.parse("2026-07-29T10:15:30Z");
            Instant updatedAt = Instant.parse("2026-07-29T11:15:30Z");

            Expense expense =
                    Expense.stored(42L, 1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, createdAt, updatedAt);

            assertThat(expense.id()).contains(42L);
            assertThat(expense.userId()).isEqualTo(1L);
            assertThat(expense.categoryId()).isEqualTo(2L);
            assertThat(expense.description()).isEqualTo("Coffee");
            assertThat(expense.merchant()).contains("Blue Bottle");
            assertThat(expense.money()).isEqualTo(MONEY);
            assertThat(expense.createdAt()).isEqualTo(createdAt);
            assertThat(expense.updatedAt()).isEqualTo(updatedAt);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName(
                "when a database id and a blank description are given, with every other field valid - then throws InvalidExpenseException")
        void whenDatabaseIdAndBlankDescriptionAreGivenWithEveryOtherFieldValid_thenThrowsInvalidExpenseException(
                String description) {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            assertThatThrownBy(
                            () -> Expense.stored(42L, 1L, 2L, description, Optional.of("Blue Bottle"), MONEY, now, now))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when a database id and an absent created_at are given - then throws InvalidExpenseException")
        void whenDatabaseIdAndAbsentCreatedAtAreGiven_thenThrowsInvalidExpenseException() {
            Instant updatedAt = Instant.parse("2026-07-29T10:15:30Z");

            assertThatThrownBy(() ->
                            Expense.stored(42L, 1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, null, updatedAt))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when a database id and an absent updated_at are given - then throws InvalidExpenseException")
        void whenDatabaseIdAndAbsentUpdatedAtAreGiven_thenThrowsInvalidExpenseException() {
            Instant createdAt = Instant.parse("2026-07-29T10:15:30Z");

            assertThatThrownBy(() ->
                            Expense.stored(42L, 1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, createdAt, null))
                    .isInstanceOf(InvalidExpenseException.class);
        }
    }
}
