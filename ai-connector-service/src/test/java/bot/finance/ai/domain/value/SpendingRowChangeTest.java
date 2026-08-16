package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.common.fixtures.RecordedChangeFixtures;
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

class SpendingRowChangeTest {

    @Nested
    @DisplayName("constructing a SpendingRowChange")
    class CompactConstructor {

        private static Stream<Arguments> invalidPresenceScenarios() {
            SpendingRow row = RecordedChangeFixtures.spendingRow();
            return Stream.of(
                    Arguments.of(Named.of("CREATED with no after", (ThrowingCallable) () -> new SpendingRowChange(
                            SpendingKind.EXPENSE,
                            ChangeOperation.CREATED,
                            RecordedChangeFixtures.DEFAULT_TRANSACTION_ID,
                            Optional.empty(),
                            Optional.empty()))),
                    Arguments.of(Named.of("DELETED with no before", (ThrowingCallable) () -> new SpendingRowChange(
                            SpendingKind.EXPENSE,
                            ChangeOperation.DELETED,
                            RecordedChangeFixtures.DEFAULT_TRANSACTION_ID,
                            Optional.empty(),
                            Optional.empty()))),
                    Arguments.of(Named.of("UPDATED with no before", (ThrowingCallable) () -> new SpendingRowChange(
                            SpendingKind.EXPENSE,
                            ChangeOperation.UPDATED,
                            RecordedChangeFixtures.DEFAULT_TRANSACTION_ID,
                            Optional.empty(),
                            Optional.of(row)))),
                    Arguments.of(Named.of("UPDATED with no after", (ThrowingCallable) () -> new SpendingRowChange(
                            SpendingKind.EXPENSE,
                            ChangeOperation.UPDATED,
                            RecordedChangeFixtures.DEFAULT_TRANSACTION_ID,
                            Optional.of(row),
                            Optional.empty()))));
        }

        @ParameterizedTest
        @MethodSource("invalidPresenceScenarios")
        @DisplayName("when CREATED lacks after, DELETED lacks before, or UPDATED misses either side - "
                + "then throws InvalidValueException")
        void whenSidePresenceViolatesOp_thenThrowsInvalidValueException(ThrowingCallable constructor) {
            assertThatThrownBy(constructor).isInstanceOf(InvalidValueException.class);
        }

        private static Stream<Arguments> invalidScalarFieldScenarios() {
            SpendingRow row = RecordedChangeFixtures.spendingRow();
            Optional<SpendingRow> after = Optional.of(row);
            return Stream.of(
                    Arguments.of(Named.of("null transactionId", (ThrowingCallable) () -> new SpendingRowChange(
                            SpendingKind.EXPENSE, ChangeOperation.CREATED, null, Optional.empty(), after))),
                    Arguments.of(Named.of("blank transactionId", (ThrowingCallable) () -> new SpendingRowChange(
                            SpendingKind.EXPENSE, ChangeOperation.CREATED, "   ", Optional.empty(), after))),
                    Arguments.of(Named.of("null kind", (ThrowingCallable) () -> new SpendingRowChange(
                            null,
                            ChangeOperation.CREATED,
                            RecordedChangeFixtures.DEFAULT_TRANSACTION_ID,
                            Optional.empty(),
                            after))),
                    Arguments.of(Named.of("null op", (ThrowingCallable) () -> new SpendingRowChange(
                            SpendingKind.EXPENSE,
                            null,
                            RecordedChangeFixtures.DEFAULT_TRANSACTION_ID,
                            Optional.empty(),
                            after))));
        }

        @ParameterizedTest
        @MethodSource("invalidScalarFieldScenarios")
        @DisplayName("when transactionId is null or blank, or kind or op is null - then throws InvalidValueException")
        void whenTransactionIdKindOrOpIsInvalid_thenThrowsInvalidValueException(ThrowingCallable constructor) {
            assertThatThrownBy(constructor).isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("reading the row a SpendingRowChange carries")
    class RowMethod {

        @Test
        @DisplayName("when the change is UPDATED with both sides - then it is the after row")
        void whenUpdatedWithBothSides_thenItIsTheAfterRow() {
            SpendingRow before = RecordedChangeFixtures.spendingRow();
            SpendingRow after = RecordedChangeFixtures.spendingRow(2L, Optional.of("msg-2"));
            SpendingRowChange change = new SpendingRowChange(
                    SpendingKind.EXPENSE,
                    ChangeOperation.UPDATED,
                    RecordedChangeFixtures.DEFAULT_TRANSACTION_ID,
                    Optional.of(before),
                    Optional.of(after));

            assertThat(change.row()).isEqualTo(after);
        }

        @Test
        @DisplayName("when the change is DELETED - then it is the before row")
        void whenDeleted_thenItIsTheBeforeRow() {
            SpendingRowChange change = RecordedChangeFixtures.expenseDeleted();

            assertThat(change.row()).isEqualTo(change.before().orElseThrow());
        }
    }

    @Nested
    @DisplayName("reading the message identity a SpendingRowChange carries")
    class MessageIdentityMethod {

        @Test
        @DisplayName("when the change's row carries a message id - then it is that row's identity")
        void whenRowCarriesMessageId_thenItIsThatRowsIdentity() {
            SpendingRowChange change = RecordedChangeFixtures.expenseCreated();

            assertThat(change.messageIdentity())
                    .contains(new MessageIdentity(
                            RecordedChangeFixtures.DEFAULT_USER_ID, RecordedChangeFixtures.DEFAULT_MESSAGE_ID));
        }
    }
}
