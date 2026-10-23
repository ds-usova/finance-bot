package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidTurnEndException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EndTurnCommandTest {

    private static final AuthenticatedUserId USER = new AuthenticatedUserId(7L);
    private static final IncomingMessageId REFERENCE = new IncomingMessageId("777:1");

    @Nested
    @DisplayName("EndTurnCommand()")
    class CommandConstructor {

        @Test
        @DisplayName("when the user id is null - then InvalidTurnEndException is thrown")
        void whenUserIdIsNull_thenThrowsInvalidTurnEndException() {
            assertThatThrownBy(() -> new EndTurnCommand(null, REFERENCE, TurnEnding.ACTED_ON))
                    .isInstanceOf(InvalidTurnEndException.class);
        }

        @Test
        @DisplayName("when the reference is null - then InvalidTurnEndException is thrown")
        void whenReferenceIsNull_thenThrowsInvalidTurnEndException() {
            assertThatThrownBy(() -> new EndTurnCommand(USER, null, TurnEnding.ACTED_ON))
                    .isInstanceOf(InvalidTurnEndException.class);
        }

        @Test
        @DisplayName("when the ending is null - then InvalidTurnEndException is thrown")
        void whenEndingIsNull_thenThrowsInvalidTurnEndException() {
            assertThatThrownBy(() -> new EndTurnCommand(USER, REFERENCE, null))
                    .isInstanceOf(InvalidTurnEndException.class);
        }
    }
}
