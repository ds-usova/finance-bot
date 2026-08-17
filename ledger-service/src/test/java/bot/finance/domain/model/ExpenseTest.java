package bot.finance.domain.model;

import static bot.finance.common.fixtures.IncomingMessages.newIncomingMessageId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.IncomingMessageId;
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
    private static final IncomingMessageId MESSAGE_REFERENCE = newIncomingMessageId();
    private static final Instant NOW = Instant.parse("2026-07-29T10:15:30Z");
    private static final Instant AN_HOUR_LATER = Instant.parse("2026-07-29T11:15:30Z");

    @Nested
    @DisplayName("creating a new expense")
    class NewExpenseFactory {

        @Test
        @DisplayName("when every field is given - then returns an expense carrying them all, unstored and stamped "
                + "with that instant")
        void whenAllFieldsAreGiven_thenReturnsExpenseCarryingThemUnstoredAndStampedWithThatInstant() {
            Expense expense = Expense.newExpense(1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, NOW);

            assertThat(expense.id()).isEmpty();
            assertThat(expense.userId()).isEqualTo(1L);
            assertThat(expense.categoryId()).isEqualTo(2L);
            assertThat(expense.description()).isEqualTo("Coffee");
            assertThat(expense.merchant()).contains("Blue Bottle");
            assertThat(expense.money()).isEqualTo(MONEY);
            assertThat(expense.status()).isEqualTo(ExpenseStatus.RECORDED);
            assertThat(expense.incomingMessageId()).isEmpty();
            assertThat(expense.createdAt()).isEqualTo(NOW);
            assertThat(expense.updatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("when the merchant is absent - then returns an expense whose merchant is empty")
        void whenMerchantIsAbsent_thenReturnsExpenseWhoseMerchantIsEmpty() {
            Expense expense = Expense.newExpense(1L, 2L, "Coffee", Optional.empty(), MONEY, NOW);

            assertThat(expense.merchant()).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the description is absent, empty, or only whitespace - then throws InvalidExpenseException")
        void whenDescriptionIsAbsentEmptyOrWhitespace_thenThrowsInvalidExpenseException(String description) {
            assertThatThrownBy(() -> Expense.newExpense(1L, 2L, description, Optional.of("Blue Bottle"), MONEY, NOW))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when money is absent - then throws InvalidExpenseException")
        void whenMoneyIsAbsent_thenThrowsInvalidExpenseException() {
            assertThatThrownBy(() -> Expense.newExpense(1L, 2L, "Coffee", Optional.of("Blue Bottle"), null, NOW))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the user id is zero or negative - then throws InvalidExpenseException")
        void whenUserIdIsZeroOrNegative_thenThrowsInvalidExpenseException(long userId) {
            assertThatThrownBy(() -> Expense.newExpense(userId, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, NOW))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the category id is zero or negative - then throws InvalidExpenseException")
        void whenCategoryIdIsZeroOrNegative_thenThrowsInvalidExpenseException(long categoryId) {
            assertThatThrownBy(
                            () -> Expense.newExpense(1L, categoryId, "Coffee", Optional.of("Blue Bottle"), MONEY, NOW))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when the merchant Optional is absent - then throws InvalidExpenseException")
        void whenMerchantOptionalIsAbsent_thenThrowsInvalidExpenseException() {
            assertThatThrownBy(() -> Expense.newExpense(1L, 2L, "Coffee", (Optional<String>) null, MONEY, NOW))
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
    @DisplayName("creating a new proposal")
    class NewProposalFactory {

        @Test
        @DisplayName("when every field is given - then the proposal is PENDING and stamped with the given instant")
        void whenEveryFieldAndAnIncomingMessageIdAreGiven_thenReturnsAPendingProposalStampedWithThatInstant() {
            Expense expense =
                    Expense.newProposal(1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, MESSAGE_REFERENCE, NOW);

            assertThat(expense.id()).isEmpty();
            assertThat(expense.userId()).isEqualTo(1L);
            assertThat(expense.categoryId()).isEqualTo(2L);
            assertThat(expense.description()).isEqualTo("Coffee");
            assertThat(expense.merchant()).contains("Blue Bottle");
            assertThat(expense.money()).isEqualTo(MONEY);
            assertThat(expense.status()).isEqualTo(ExpenseStatus.PENDING);
            assertThat(expense.incomingMessageId()).contains(MESSAGE_REFERENCE);
            assertThat(expense.createdAt()).isEqualTo(NOW);
            assertThat(expense.updatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("when every other field is valid and no incoming message id is given - then throws "
                + "InvalidExpenseException")
        void whenEveryOtherFieldIsValidAndNoIncomingMessageIdIsGiven_thenThrowsInvalidExpenseException() {
            assertThatThrownBy(
                            () -> Expense.newProposal(1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, null, NOW))
                    .isInstanceOf(InvalidExpenseException.class);
        }
    }

    @Nested
    @DisplayName("reconstituting a stored expense")
    class StoredFactory {

        @Test
        @DisplayName("when a database id and every other field are given - then returns an expense carrying them "
                + "all, timestamps unchanged")
        void whenDatabaseIdAndEveryOtherFieldAreGiven_thenReturnsExpenseCarryingAllWithTimestampsUnchanged() {
            Expense expense = Expense.stored(
                    42L,
                    1L,
                    2L,
                    "Coffee",
                    Optional.of("Blue Bottle"),
                    MONEY,
                    ExpenseStatus.RECORDED,
                    Optional.empty(),
                    NOW,
                    AN_HOUR_LATER);

            assertThat(expense.id()).contains(42L);
            assertThat(expense.userId()).isEqualTo(1L);
            assertThat(expense.categoryId()).isEqualTo(2L);
            assertThat(expense.description()).isEqualTo("Coffee");
            assertThat(expense.merchant()).contains("Blue Bottle");
            assertThat(expense.money()).isEqualTo(MONEY);
            assertThat(expense.createdAt()).isEqualTo(NOW);
            assertThat(expense.updatedAt()).isEqualTo(AN_HOUR_LATER);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when a database id and a blank description are given - then throws InvalidExpenseException")
        void whenDatabaseIdAndBlankDescriptionAreGiven_thenThrowsInvalidExpenseException(String description) {
            assertThatThrownBy(() -> Expense.stored(
                            42L,
                            1L,
                            2L,
                            description,
                            Optional.of("Blue Bottle"),
                            MONEY,
                            ExpenseStatus.RECORDED,
                            Optional.empty(),
                            NOW,
                            NOW))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when a database id and an absent created_at are given - then throws InvalidExpenseException")
        void whenDatabaseIdAndAbsentCreatedAtAreGiven_thenThrowsInvalidExpenseException() {
            assertThatThrownBy(() -> Expense.stored(
                            42L,
                            1L,
                            2L,
                            "Coffee",
                            Optional.of("Blue Bottle"),
                            MONEY,
                            ExpenseStatus.RECORDED,
                            Optional.empty(),
                            null,
                            NOW))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when a database id and an absent updated_at are given - then throws InvalidExpenseException")
        void whenDatabaseIdAndAbsentUpdatedAtAreGiven_thenThrowsInvalidExpenseException() {
            assertThatThrownBy(() -> Expense.stored(
                            42L,
                            1L,
                            2L,
                            "Coffee",
                            Optional.of("Blue Bottle"),
                            MONEY,
                            ExpenseStatus.RECORDED,
                            Optional.empty(),
                            NOW,
                            null))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when a database id, status and message id are given - then the entry carries them unchanged")
        void whenDatabaseIdPendingAndAnIncomingMessageIdAreGiven_thenReturnsTheEntryCarryingThemUnchanged() {
            Expense expense = Expense.stored(
                    42L,
                    1L,
                    2L,
                    "Coffee",
                    Optional.of("Blue Bottle"),
                    MONEY,
                    ExpenseStatus.PENDING,
                    Optional.of(MESSAGE_REFERENCE),
                    NOW,
                    AN_HOUR_LATER);

            assertThat(expense.id()).contains(42L);
            assertThat(expense.status()).isEqualTo(ExpenseStatus.PENDING);
            assertThat(expense.incomingMessageId()).contains(MESSAGE_REFERENCE);
            assertThat(expense.createdAt()).isEqualTo(NOW);
            assertThat(expense.updatedAt()).isEqualTo(AN_HOUR_LATER);
        }

        @Test
        @DisplayName("when a database id, PENDING and no incoming message id are given - then throws "
                + "InvalidExpenseException")
        void whenDatabaseIdPendingAndNoIncomingMessageIdAreGiven_thenThrowsInvalidExpenseException() {
            assertThatThrownBy(() -> Expense.stored(
                            42L,
                            1L,
                            2L,
                            "Coffee",
                            Optional.of("Blue Bottle"),
                            MONEY,
                            ExpenseStatus.PENDING,
                            Optional.empty(),
                            NOW,
                            NOW))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when a database id, RECORDED and no incoming message id are given - then the entry is "
                + "returned with an empty message id")
        void whenDatabaseIdRecordedAndNoIncomingMessageIdAreGiven_thenReturnsTheEntryWithAnEmptyMessageId() {
            Expense expense = Expense.stored(
                    42L,
                    1L,
                    2L,
                    "Coffee",
                    Optional.of("Blue Bottle"),
                    MONEY,
                    ExpenseStatus.RECORDED,
                    Optional.empty(),
                    NOW,
                    NOW);

            assertThat(expense.incomingMessageId()).isEmpty();
        }

        @Test
        @DisplayName("when a database id, PENDING and a null incoming message id are given - then throws "
                + "InvalidExpenseException")
        void whenDatabaseIdPendingAndNullIncomingMessageIdAreGiven_thenThrowsInvalidExpenseException() {
            assertThatThrownBy(() -> Expense.stored(
                            42L,
                            1L,
                            2L,
                            "Coffee",
                            Optional.of("Blue Bottle"),
                            MONEY,
                            ExpenseStatus.PENDING,
                            null,
                            NOW,
                            NOW))
                    .isInstanceOf(InvalidExpenseException.class);
        }

        @Test
        @DisplayName("when a database id, RECORDED and a null incoming message id are given - then throws "
                + "InvalidExpenseException")
        void whenDatabaseIdRecordedAndNullIncomingMessageIdAreGiven_thenThrowsInvalidExpenseException() {
            assertThatThrownBy(() -> Expense.stored(
                            42L,
                            1L,
                            2L,
                            "Coffee",
                            Optional.of("Blue Bottle"),
                            MONEY,
                            ExpenseStatus.RECORDED,
                            null,
                            NOW,
                            NOW))
                    .isInstanceOf(InvalidExpenseException.class);
        }
    }
}
