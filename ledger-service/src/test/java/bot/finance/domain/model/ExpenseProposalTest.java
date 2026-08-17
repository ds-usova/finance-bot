package bot.finance.domain.model;

import static bot.finance.common.fixtures.IncomingMessages.newIncomingMessageId;

import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.Money;
import java.time.Instant;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@Disabled("RU01: ExpenseProposal is gone, its PENDING cases are rewritten onto ExpenseTest")
class ExpenseProposalTest {

    private static final Money MONEY = new Money(1500L, CurrencyCode.of("USD"));
    private static final IncomingMessageId MESSAGE_REFERENCE = newIncomingMessageId();

    @Nested
    @DisplayName("creating a new expense proposal")
    class NewExpenseProposalFactory {

        @Test
        @DisplayName("when every field is given - then returns a proposal carrying them all, unstored and stamped "
                + "with that instant")
        void whenAllFieldsAreGiven_thenReturnsProposalCarryingThemUnstoredAndStampedWithThatInstant() {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            // ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
            //         1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, MESSAGE_REFERENCE, now);
            //
            // assertThat(proposal.id()).isEmpty();
            // assertThat(proposal.userId()).isEqualTo(1L);
            // assertThat(proposal.categoryId()).isEqualTo(2L);
            // assertThat(proposal.description()).isEqualTo("Coffee");
            // assertThat(proposal.merchant()).contains("Blue Bottle");
            // assertThat(proposal.money()).isEqualTo(MONEY);
            // assertThat(proposal.incomingMessageId()).isEqualTo(MESSAGE_REFERENCE);
            // assertThat(proposal.createdAt()).isEqualTo(now);
            // assertThat(proposal.updatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("when the merchant is absent - then returns a proposal whose merchant is empty")
        void whenMerchantIsAbsent_thenReturnsProposalWhoseMerchantIsEmpty() {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            // ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
            //         1L, 2L, "Coffee", Optional.empty(), MONEY, MESSAGE_REFERENCE, now);
            //
            // assertThat(proposal.merchant()).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName(
                "when the description is absent, empty, or only whitespace - then throws InvalidExpenseProposalException")
        void whenDescriptionIsAbsentEmptyOrWhitespace_thenThrowsInvalidExpenseProposalException(String description) {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            // assertThatThrownBy(() -> ExpenseProposal.newExpenseProposal(
            //                 1L, 2L, description, Optional.of("Blue Bottle"), MONEY, MESSAGE_REFERENCE, now))
            //         .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName("when money is absent - then throws InvalidExpenseProposalException")
        void whenMoneyIsAbsent_thenThrowsInvalidExpenseProposalException() {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            // assertThatThrownBy(() -> ExpenseProposal.newExpenseProposal(
            //                 1L, 2L, "Coffee", Optional.of("Blue Bottle"), null, MESSAGE_REFERENCE, now))
            //         .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the user id is zero or negative - then throws InvalidExpenseProposalException")
        void whenUserIdIsZeroOrNegative_thenThrowsInvalidExpenseProposalException(long userId) {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            // assertThatThrownBy(() -> ExpenseProposal.newExpenseProposal(
            //                 userId, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, MESSAGE_REFERENCE, now))
            //         .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the category id is zero or negative - then throws InvalidExpenseProposalException")
        void whenCategoryIdIsZeroOrNegative_thenThrowsInvalidExpenseProposalException(long categoryId) {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            // assertThatThrownBy(() -> ExpenseProposal.newExpenseProposal(
            //                 1L, categoryId, "Coffee", Optional.of("Blue Bottle"), MONEY, MESSAGE_REFERENCE, now))
            //         .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName("when the merchant Optional is absent - then throws InvalidExpenseProposalException")
        void whenMerchantOptionalIsAbsent_thenThrowsInvalidExpenseProposalException() {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            // assertThatThrownBy(() -> ExpenseProposal.newExpenseProposal(
            //                 1L, 2L, "Coffee", (Optional<String>) null, MONEY, MESSAGE_REFERENCE, now))
            //         .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName("when the instant is absent - then throws InvalidExpenseProposalException")
        void whenInstantIsAbsent_thenThrowsInvalidExpenseProposalException() {
            // assertThatThrownBy(() -> ExpenseProposal.newExpenseProposal(
            //                 1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, MESSAGE_REFERENCE, null))
            //         .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName("when the message reference is absent - then throws InvalidExpenseProposalException")
        void whenMessageReferenceIsAbsent_thenThrowsInvalidExpenseProposalException() {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            // assertThatThrownBy(() -> ExpenseProposal.newExpenseProposal(
            //                 1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, null, now))
            //         .isInstanceOf(InvalidExpenseProposalException.class);
        }
    }

    @Nested
    @DisplayName("reconstituting a stored expense proposal")
    class StoredFactory {

        @Test
        @DisplayName("when a database id and every other field are given - then returns a proposal carrying them "
                + "all, timestamps unchanged")
        void whenDatabaseIdAndEveryOtherFieldAreGiven_thenReturnsProposalCarryingAllWithTimestampsUnchanged() {
            Instant createdAt = Instant.parse("2026-07-29T10:15:30Z");
            Instant updatedAt = Instant.parse("2026-07-29T11:15:30Z");

            // ExpenseProposal proposal = ExpenseProposal.stored(
            //         42L, 1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, MESSAGE_REFERENCE, createdAt,
            // updatedAt);
            //
            // assertThat(proposal.id()).contains(42L);
            // assertThat(proposal.userId()).isEqualTo(1L);
            // assertThat(proposal.categoryId()).isEqualTo(2L);
            // assertThat(proposal.description()).isEqualTo("Coffee");
            // assertThat(proposal.merchant()).contains("Blue Bottle");
            // assertThat(proposal.money()).isEqualTo(MONEY);
            // assertThat(proposal.incomingMessageId()).isEqualTo(MESSAGE_REFERENCE);
            // assertThat(proposal.createdAt()).isEqualTo(createdAt);
            // assertThat(proposal.updatedAt()).isEqualTo(updatedAt);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when a database id and a blank description are given - then throws "
                + "InvalidExpenseProposalException")
        void whenDatabaseIdAndBlankDescriptionAreGiven_thenThrowsInvalidExpenseProposalException(String description) {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            // assertThatThrownBy(() -> ExpenseProposal.stored(
            //                 42L, 1L, 2L, description, Optional.of("Blue Bottle"), MONEY, MESSAGE_REFERENCE, now,
            // now))
            //         .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName(
                "when a database id and an absent created_at are given - then throws InvalidExpenseProposalException")
        void whenDatabaseIdAndAbsentCreatedAtAreGiven_thenThrowsInvalidExpenseProposalException() {
            Instant updatedAt = Instant.parse("2026-07-29T10:15:30Z");

            // assertThatThrownBy(() -> ExpenseProposal.stored(
            //                 42L,
            //                 1L,
            //                 2L,
            //                 "Coffee",
            //                 Optional.of("Blue Bottle"),
            //                 MONEY,
            //                 MESSAGE_REFERENCE,
            //                 null,
            //                 updatedAt))
            //         .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName(
                "when a database id and an absent updated_at are given - then throws InvalidExpenseProposalException")
        void whenDatabaseIdAndAbsentUpdatedAtAreGiven_thenThrowsInvalidExpenseProposalException() {
            Instant createdAt = Instant.parse("2026-07-29T10:15:30Z");

            // assertThatThrownBy(() -> ExpenseProposal.stored(
            //                 42L,
            //                 1L,
            //                 2L,
            //                 "Coffee",
            //                 Optional.of("Blue Bottle"),
            //                 MONEY,
            //                 MESSAGE_REFERENCE,
            //                 createdAt,
            //                 null))
            //         .isInstanceOf(InvalidExpenseProposalException.class);
        }

        @Test
        @DisplayName("when a database id and an absent message reference are given - then throws "
                + "InvalidExpenseProposalException")
        void whenDatabaseIdAndAbsentMessageReferenceAreGiven_thenThrowsInvalidExpenseProposalException() {
            Instant now = Instant.parse("2026-07-29T10:15:30Z");

            // assertThatThrownBy(() -> ExpenseProposal.stored(
            //                 42L, 1L, 2L, "Coffee", Optional.of("Blue Bottle"), MONEY, null, now, now))
            //         .isInstanceOf(InvalidExpenseProposalException.class);
        }
    }
}
