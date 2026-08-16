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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SpendingRowTest {

    private static SpendingRow row(
            Optional<String> incomingMessageId,
            String description,
            Optional<String> merchant,
            CurrencyCode currencyCode,
            Optional<String> categoryName,
            Optional<String> groupingName) {
        return new SpendingRow(
                RecordedChangeFixtures.DEFAULT_ROW_ID,
                RecordedChangeFixtures.DEFAULT_USER_ID,
                incomingMessageId,
                description,
                merchant,
                RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS,
                currencyCode,
                RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                categoryName,
                groupingName);
    }

    private static SpendingRow validRow() {
        return row(
                Optional.of(RecordedChangeFixtures.DEFAULT_MESSAGE_ID),
                RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                Optional.empty(),
                CurrencyCode.of(RecordedChangeFixtures.DEFAULT_CURRENCY),
                Optional.of(RecordedChangeFixtures.DEFAULT_CATEGORY_NAME),
                Optional.of(RecordedChangeFixtures.DEFAULT_GROUPING_NAME));
    }

    @Nested
    @DisplayName("constructing a SpendingRow")
    class CompactConstructor {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when description is null or blank - then throws InvalidValueException")
        void whenDescriptionIsNullOrBlank_thenThrowsInvalidValueException(String description) {
            SpendingRow valid = validRow();
            assertThatThrownBy(() -> row(
                            valid.incomingMessageId(),
                            description,
                            valid.merchant(),
                            valid.currencyCode(),
                            valid.categoryName(),
                            valid.groupingName()))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when currencyCode is null - then throws InvalidValueException")
        void whenCurrencyCodeIsNull_thenThrowsInvalidValueException() {
            SpendingRow valid = validRow();
            assertThatThrownBy(() -> row(
                            valid.incomingMessageId(),
                            valid.description(),
                            valid.merchant(),
                            null,
                            valid.categoryName(),
                            valid.groupingName()))
                    .isInstanceOf(InvalidValueException.class);
        }

        static Stream<Arguments> nullOptionalFieldScenarios() {
            SpendingRow valid = validRow();
            return Stream.of(
                    Arguments.of(Named.of("merchant", (ThrowingCallable) () -> row(
                            valid.incomingMessageId(),
                            valid.description(),
                            null,
                            valid.currencyCode(),
                            valid.categoryName(),
                            valid.groupingName()))),
                    Arguments.of(Named.of("incomingMessageId", (ThrowingCallable) () -> row(
                            null,
                            valid.description(),
                            valid.merchant(),
                            valid.currencyCode(),
                            valid.categoryName(),
                            valid.groupingName()))),
                    Arguments.of(Named.of("categoryName", (ThrowingCallable) () -> row(
                            valid.incomingMessageId(),
                            valid.description(),
                            valid.merchant(),
                            valid.currencyCode(),
                            null,
                            valid.groupingName()))),
                    Arguments.of(Named.of("groupingName", (ThrowingCallable) () -> row(
                            valid.incomingMessageId(),
                            valid.description(),
                            valid.merchant(),
                            valid.currencyCode(),
                            valid.categoryName(),
                            null))));
        }

        @ParameterizedTest
        @MethodSource("nullOptionalFieldScenarios")
        @DisplayName("when merchant, incomingMessageId, categoryName or groupingName Optional is null - "
                + "then throws InvalidValueException")
        void whenAnOptionalFieldIsNull_thenThrowsInvalidValueException(ThrowingCallable constructor) {
            assertThatThrownBy(constructor).isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("reading the message identity a SpendingRow carries")
    class MessageIdentityMethod {

        @Test
        @DisplayName("when the row carries an incoming message id - then it carries the row's userId and that id")
        void whenRowCarriesIncomingMessageId_thenIdentityCarriesUserIdAndMessageId() {
            SpendingRow row = RecordedChangeFixtures.spendingRow(
                    RecordedChangeFixtures.DEFAULT_ROW_ID, Optional.of(RecordedChangeFixtures.DEFAULT_MESSAGE_ID));

            assertThat(row.messageIdentity())
                    .contains(new MessageIdentity(
                            RecordedChangeFixtures.DEFAULT_USER_ID, RecordedChangeFixtures.DEFAULT_MESSAGE_ID));
        }

        @Test
        @DisplayName("when the row has no incoming message id - then it is empty")
        void whenRowHasNoIncomingMessageId_thenItIsEmpty() {
            SpendingRow row =
                    RecordedChangeFixtures.spendingRow(RecordedChangeFixtures.DEFAULT_ROW_ID, Optional.empty());

            assertThat(row.messageIdentity()).isEmpty();
        }
    }
}
