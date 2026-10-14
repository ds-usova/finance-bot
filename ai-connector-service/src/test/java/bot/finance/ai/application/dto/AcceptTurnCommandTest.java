package bot.finance.ai.application.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AcceptTurnCommandTest {

    private static final MessageIdentity IDENTITY = new MessageIdentity(42L, "msg-123");

    private static ExtractIntentsCommand turn() {
        return new ExtractIntentsCommand(
                "spent 15 euros on lunch",
                List.of("Dining", "Miscellaneous"),
                "Miscellaneous",
                Optional.empty(),
                LocalDate.of(2026, 10, 13),
                Optional.of(IDENTITY),
                List.of(
                        new CatalogueEntry("Dining", List.of("Restaurants")),
                        new CatalogueEntry("Miscellaneous", List.of("Uncategorized Expenses"))));
    }

    @Nested
    @DisplayName("constructing the command")
    class Construction {

        static Stream<Arguments> missingParts() {
            return Stream.of(Arguments.of("null turn", null, IDENTITY), Arguments.of("null turn key", turn(), null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("missingParts")
        @DisplayName("when the turn or the turn key is null - then throws InvalidValueException")
        void whenTurnOrTurnKeyIsNull_thenThrowsInvalidValueException(
                String name, ExtractIntentsCommand turn, MessageIdentity turnKey) {
            assertThatThrownBy(() -> new AcceptTurnCommand(turn, turnKey)).isInstanceOf(InvalidValueException.class);
        }
    }
}
