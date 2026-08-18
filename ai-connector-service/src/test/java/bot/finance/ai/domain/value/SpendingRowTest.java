package bot.finance.ai.domain.value;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpendingRowTest {

    @Nested
    @DisplayName("constructing a SpendingRow")
    class CompactConstructor {

        @Disabled("RU01: rewritten against the reshaped fields - expenseId, amount, category, grouping")
        @Test
        @DisplayName("when description is null or blank - then throws InvalidValueException")
        void whenDescriptionIsNullOrBlank_thenThrowsInvalidValueException() {}

        @Disabled("RU01: rewritten against the reshaped fields - expenseId, amount, category, grouping")
        @Test
        @DisplayName("when currencyCode is null - then throws InvalidValueException")
        void whenCurrencyCodeIsNull_thenThrowsInvalidValueException() {}

        @Disabled("RU01: rewritten against the reshaped fields - expenseId, amount, category, grouping")
        @Test
        @DisplayName("when merchant, incomingMessageId, categoryName or groupingName Optional is null - "
                + "then throws InvalidValueException")
        void whenAnOptionalFieldIsNull_thenThrowsInvalidValueException() {}
    }

    @Nested
    @DisplayName("reading the message identity a SpendingRow carries")
    class MessageIdentityMethod {

        @Disabled("RU01: rewritten against SpendingFactFixtures")
        @Test
        @DisplayName("when the row carries an incoming message id - then it carries the row's userId and that id")
        void whenRowCarriesIncomingMessageId_thenIdentityCarriesUserIdAndMessageId() {}

        @Disabled("RU01: rewritten against SpendingFactFixtures")
        @Test
        @DisplayName("when the row has no incoming message id - then it is empty")
        void whenRowHasNoIncomingMessageId_thenItIsEmpty() {}
    }
}
