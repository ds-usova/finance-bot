package bot.finance.ai.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OperationTest {

    @Nested
    @DisplayName("matching a label to an Operation")
    class FromLabel {

        @ParameterizedTest
        @CsvSource({
                "create, CREATE",
                "read, READ",
                "update, UPDATE",
                "delete, DELETE"
        })
        @DisplayName("when the label is create, read, update, or delete - then returns the matching operation")
        void whenLabelIsCreateReadUpdateOrDelete_thenReturnsMatchingOperation(String label, Operation expected) {
            assertThat(Operation.fromLabel(label)).contains(expected);
        }

        @ParameterizedTest
        @CsvSource(value = {
                "CREATE,CREATE",
                "ReAd,READ",
                " update ,UPDATE",
                "DELETE   ,DELETE"
        }, ignoreLeadingAndTrailingWhitespace = false)
        @DisplayName("when a label differing only in case or surrounded by whitespace is given - "
                + "then returns the matching operation")
        void whenLabelDiffersInCaseOrSurroundedByWhitespace_thenReturnsMatchingOperation(
                String label, Operation expected) {
            assertThat(Operation.fromLabel(label)).contains(expected);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"unknown", "", "   "})
        @DisplayName("when the label is unrecognized, blank, or null - then returns an empty Optional "
                + "rather than throwing")
        void whenLabelIsUnrecognizedBlankOrNull_thenReturnsEmptyOptional(String label) {
            assertThat(Operation.fromLabel(label)).isEmpty();
        }

    }

}
