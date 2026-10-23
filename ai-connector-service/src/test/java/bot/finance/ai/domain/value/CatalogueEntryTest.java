package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CatalogueEntryTest {

    @Nested
    @DisplayName("constructing a CatalogueEntry")
    class Construction {

        static Stream<Arguments> invalidScenarios() {
            return Stream.of(
                    Arguments.of(Named.of(
                            "null grouping", (ThrowingCallable) () -> new CatalogueEntry(null, List.of("Coffee")))),
                    Arguments.of(Named.of(
                            "blank grouping", (ThrowingCallable) () -> new CatalogueEntry("   ", List.of("Coffee")))),
                    Arguments.of(Named.of(
                            "null category list", (ThrowingCallable) () -> new CatalogueEntry("Dining", null))),
                    Arguments.of(Named.of(
                            "empty category list", (ThrowingCallable) () -> new CatalogueEntry("Dining", List.of()))),
                    Arguments.of(Named.of("null category name", (ThrowingCallable)
                            () -> new CatalogueEntry("Dining", Arrays.asList("Coffee", null)))),
                    Arguments.of(Named.of("blank category name", (ThrowingCallable)
                            () -> new CatalogueEntry("Dining", Arrays.asList("Coffee", " ")))),
                    Arguments.of(Named.of("category name twice", (ThrowingCallable)
                            () -> new CatalogueEntry("Dining", List.of("Coffee", "Coffee")))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidScenarios")
        @DisplayName("when the grouping or the category list is invalid - then throws InvalidValueException")
        void whenGroupingOrCategoriesInvalid_thenThrowsInvalidValueException(ThrowingCallable constructor) {
            assertThatThrownBy(constructor).isInstanceOf(InvalidValueException.class);
        }
    }
}
