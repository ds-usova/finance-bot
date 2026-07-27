package bot.finance.ai.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IntentTargetTest {

    @Nested
    @DisplayName("matching a label to an IntentTarget")
    class FromLabel {

        @ParameterizedTest
        @CsvSource({
                "category, CATEGORY",
                "expense, EXPENSE"
        })
        @DisplayName("when the label is category or expense - then returns the matching target")
        void whenLabelIsCategoryOrExpense_thenReturnsMatchingTarget(String label, IntentTarget expected) {
            assertThat(IntentTarget.fromLabel(label)).contains(expected);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"unknown", "", "   "})
        @DisplayName("when the label is unrecognized, blank, or null - then returns an empty Optional")
        void whenLabelIsUnrecognizedBlankOrNull_thenReturnsEmptyOptional(String label) {
            assertThat(IntentTarget.fromLabel(label)).isEmpty();
        }

    }

}
