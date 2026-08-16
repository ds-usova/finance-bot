package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.common.fixtures.RecordedChangeFixtures;
import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CategoryRowChangeTest {

    @Nested
    @DisplayName("constructing a CategoryRowChange")
    class CompactConstructor {

        private static Stream<Arguments> invalidPresenceScenarios() {
            CategoryRow row = RecordedChangeFixtures.categoryRow();
            return Stream.of(
                    Arguments.of(Named.of("CREATED with no after", (ThrowingCallable)
                            () -> new CategoryRowChange(ChangeOperation.CREATED, Optional.empty(), Optional.empty()))),
                    Arguments.of(Named.of("DELETED with no before", (ThrowingCallable)
                            () -> new CategoryRowChange(ChangeOperation.DELETED, Optional.empty(), Optional.empty()))),
                    Arguments.of(Named.of("UPDATED with no before", (ThrowingCallable)
                            () -> new CategoryRowChange(ChangeOperation.UPDATED, Optional.empty(), Optional.of(row)))),
                    Arguments.of(Named.of("UPDATED with no after", (ThrowingCallable)
                            () -> new CategoryRowChange(ChangeOperation.UPDATED, Optional.of(row), Optional.empty()))));
        }

        @ParameterizedTest
        @MethodSource("invalidPresenceScenarios")
        @DisplayName("when CREATED lacks after, DELETED lacks before, or UPDATED misses either side - "
                + "then throws InvalidValueException")
        void whenSidePresenceViolatesOp_thenThrowsInvalidValueException(ThrowingCallable constructor) {
            assertThatThrownBy(constructor).isInstanceOf(InvalidValueException.class);
        }
    }
}
