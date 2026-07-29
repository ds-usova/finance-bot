package bot.finance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    }

    @Nested
    @DisplayName("comparing expenses for equality")
    class Equality {

        @Test
        @DisplayName(
                "when two stored expenses share the same database id but differ in every other field - then they are equal and their hash codes match")
        void whenTwoStoredExpensesShareDatabaseIdButDifferInEveryOtherField_thenTheyAreEqualAndHashCodesMatch() {
            Instant firstInstant = Instant.parse("2026-07-29T10:15:30Z");
            Instant secondInstant = Instant.parse("2026-01-01T00:00:00Z");
            Expense first =
                    Expense.stored(1L, 1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, firstInstant, firstInstant);
            Expense second = Expense.stored(
                    1L,
                    9L,
                    8L,
                    "Rent",
                    Optional.empty(),
                    new Money(50000L, CurrencyCode.of("EUR")),
                    secondInstant,
                    secondInstant);

            assertThat(first).isEqualTo(second);
            assertThat(first.hashCode()).isEqualTo(second.hashCode());
        }

        @Test
        @DisplayName("when two stored expenses have different database ids - then they are not equal")
        void whenTwoStoredExpensesHaveDifferentDatabaseIds_thenTheyAreNotEqual() {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");
            Expense first = Expense.stored(1L, 1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, now, now);
            Expense second = Expense.stored(2L, 1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, now, now);

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName(
                "when two unstored expenses are built from identical fields - then they are not equal, an expense with no id is only itself")
        void whenTwoUnstoredExpensesAreBuiltFromIdenticalFields_thenTheyAreNotEqual() {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");
            Expense first = Expense.newExpense(1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, now);
            Expense second = Expense.newExpense(1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, now);

            assertThat(first).isNotEqualTo(second);
        }
    }
}
