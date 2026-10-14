package bot.finance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidIncomingTurnException;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IncomingTurnTest {

    private static final Instant KEPT_AT = Instant.parse("2026-08-04T10:00:00Z");

    @Nested
    @DisplayName("newTurn()")
    class NewTurn {

        @Test
        @DisplayName("when a turn is built from every field - then it carries them and has no id, attempt or hand-over")
        void whenTurnIsBuiltFromEveryField_thenItCarriesThemAndHasNoIdAttemptOrHandOver() {
            IncomingTurn turn = IncomingTurn.newTurn(7L, "777", "1", "coffee 4.50", KEPT_AT);

            assertThat(turn.reference()).isEqualTo(new IncomingMessageId("777:1"));
            assertThat(turn.userId()).isEqualTo(7L);
            assertThat(turn.conversationId()).isEqualTo("777");
            assertThat(turn.inboundMessageId()).isEqualTo("1");
            assertThat(turn.text()).isEqualTo("coffee 4.50");
            assertThat(turn.keptAt()).isEqualTo(KEPT_AT);
            assertThat(turn.id()).isEmpty();
            assertThat(turn.lastAttemptAt()).isEmpty();
            assertThat(turn.handedOverAt()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the user id is zero or less - then InvalidIncomingTurnException is thrown")
        void whenUserIdIsZeroOrLess_thenThrowsInvalidIncomingTurnException(long userId) {
            assertThatThrownBy(() -> IncomingTurn.newTurn(userId, "777", "1", "coffee", KEPT_AT))
                    .isInstanceOf(InvalidIncomingTurnException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("when the conversation id is null or blank - then InvalidIncomingTurnException is thrown")
        void whenConversationIdIsNullOrBlank_thenThrowsInvalidIncomingTurnException(String conversationId) {
            assertThatThrownBy(() -> IncomingTurn.newTurn(7L, conversationId, "1", "coffee", KEPT_AT))
                    .isInstanceOf(InvalidIncomingTurnException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("when the inbound message id is null or blank - then InvalidIncomingTurnException is thrown")
        void whenInboundMessageIdIsNullOrBlank_thenThrowsInvalidIncomingTurnException(String inboundMessageId) {
            assertThatThrownBy(() -> IncomingTurn.newTurn(7L, "777", inboundMessageId, "coffee", KEPT_AT))
                    .isInstanceOf(InvalidIncomingTurnException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("when the text is null or blank - then InvalidIncomingTurnException is thrown")
        void whenTextIsNullOrBlank_thenThrowsInvalidIncomingTurnException(String text) {
            assertThatThrownBy(() -> IncomingTurn.newTurn(7L, "777", "1", text, KEPT_AT))
                    .isInstanceOf(InvalidIncomingTurnException.class);
        }

        @Test
        @DisplayName("when the kept-at instant is null - then InvalidIncomingTurnException is thrown")
        void whenKeptAtIsNull_thenThrowsInvalidIncomingTurnException() {
            assertThatThrownBy(() -> IncomingTurn.newTurn(7L, "777", "1", "coffee", null))
                    .isInstanceOf(InvalidIncomingTurnException.class);
        }
    }

    @Nested
    @DisplayName("stored()")
    class Stored {

        @Test
        @DisplayName("when a turn is rebuilt with an id, an attempt and a hand-over - then it answers them")
        void whenTurnIsRebuiltWithIdAttemptAndHandOver_thenItAnswersThem() {
            Instant attempt = Instant.parse("2026-08-04T10:01:00Z");
            Instant handedOver = Instant.parse("2026-08-04T10:02:00Z");

            IncomingTurn turn = IncomingTurn.stored(
                    5L, 7L, "777", "1", "coffee", KEPT_AT, Optional.of(attempt), Optional.of(handedOver));

            assertThat(turn.id()).contains(5L);
            assertThat(turn.lastAttemptAt()).contains(attempt);
            assertThat(turn.handedOverAt()).contains(handedOver);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the id is zero or less - then InvalidIncomingTurnException is thrown")
        void whenIdIsZeroOrLess_thenThrowsInvalidIncomingTurnException(long id) {
            assertThatThrownBy(() -> IncomingTurn.stored(
                            id, 7L, "777", "1", "coffee", KEPT_AT, Optional.empty(), Optional.empty()))
                    .isInstanceOf(InvalidIncomingTurnException.class);
        }

        @Test
        @DisplayName("when the last-attempt optional is null - then InvalidIncomingTurnException is thrown")
        void whenLastAttemptOptionalIsNull_thenThrowsInvalidIncomingTurnException() {
            assertThatThrownBy(() -> IncomingTurn.stored(5L, 7L, "777", "1", "coffee", KEPT_AT, null, Optional.empty()))
                    .isInstanceOf(InvalidIncomingTurnException.class);
        }

        @Test
        @DisplayName("when the hand-over optional is null - then InvalidIncomingTurnException is thrown")
        void whenHandOverOptionalIsNull_thenThrowsInvalidIncomingTurnException() {
            assertThatThrownBy(() -> IncomingTurn.stored(5L, 7L, "777", "1", "coffee", KEPT_AT, Optional.empty(), null))
                    .isInstanceOf(InvalidIncomingTurnException.class);
        }
    }

    @Nested
    @DisplayName("keptOn()")
    class KeptOn {

        @Test
        @DisplayName("when the day is read in UTC and in +02:00 - then it is the zone's own day")
        void whenDayIsReadInUtcAndInPlusTwo_thenItIsTheZonesOwnDay() {
            IncomingTurn turn = IncomingTurn.newTurn(7L, "777", "1", "coffee", Instant.parse("2026-08-04T23:59:59Z"));

            assertThat(turn.keptOn(ZoneOffset.UTC)).isEqualTo(LocalDate.parse("2026-08-04"));
            assertThat(turn.keptOn(ZoneId.of("+02:00"))).isEqualTo(LocalDate.parse("2026-08-05"));
        }
    }
}
