package bot.finance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class WrittenMessageTest {

    private static final long USER_ID = 7L;
    private static final String TEXT = "coffee 3.40, taxi home 14";
    private static final Instant NOW = Instant.parse("2026-10-02T10:15:30Z");

    @Nested
    @DisplayName("newMessage()")
    class NewMessage {

        @Test
        @DisplayName("when a message is filed - then it is waiting, has no id and carries the user, text and instant")
        void whenMessageIsFiled_thenItIsWaitingWithoutIdCarryingUserTextAndInstant() {
            WrittenMessage message = WrittenMessage.newMessage(USER_ID, TEXT, NOW);

            assertThat(message.status()).isEqualTo(WrittenMessageStatus.WAITING);
            assertThat(message.id()).isEmpty();
            assertThat(message.userId()).isEqualTo(USER_ID);
            assertThat(message.text()).isEqualTo(TEXT);
            assertThat(message.createdAt()).isEqualTo(NOW);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  \t \n \r\n "})
        @DisplayName("when the text is absent, empty or blank - then InvalidWrittenMessageException is thrown")
        void whenTextIsAbsentEmptyOrBlank_thenThrowsInvalidWrittenMessageException(String text) {
            assertThatThrownBy(() -> WrittenMessage.newMessage(USER_ID, text, NOW))
                    .isInstanceOf(InvalidWrittenMessageException.class);
        }

        @Test
        @DisplayName("when the user id is 0 - then InvalidWrittenMessageException is thrown")
        void whenUserIdIsZero_thenThrowsInvalidWrittenMessageException() {
            assertThatThrownBy(() -> WrittenMessage.newMessage(0L, TEXT, NOW))
                    .isInstanceOf(InvalidWrittenMessageException.class);
        }

        @Test
        @DisplayName("when the instant is absent - then InvalidWrittenMessageException is thrown")
        void whenInstantIsAbsent_thenThrowsInvalidWrittenMessageException() {
            assertThatThrownBy(() -> WrittenMessage.newMessage(USER_ID, TEXT, null))
                    .isInstanceOf(InvalidWrittenMessageException.class);
        }
    }

    @Nested
    @DisplayName("stored()")
    class Stored {

        @Test
        @DisplayName("when a stored message is rebuilt - then it carries the id and status it was given")
        void whenStoredMessageIsRebuilt_thenItCarriesIdAndStatus() {
            WrittenMessage message = WrittenMessage.stored(42L, USER_ID, TEXT, WrittenMessageStatus.READING, NOW);

            assertThat(message.id()).contains(42L);
            assertThat(message.status()).isEqualTo(WrittenMessageStatus.READING);
        }

        @Test
        @DisplayName("when the status is absent - then InvalidWrittenMessageException is thrown")
        void whenStatusIsAbsent_thenThrowsInvalidWrittenMessageException() {
            assertThatThrownBy(() -> WrittenMessage.stored(42L, USER_ID, TEXT, null, NOW))
                    .isInstanceOf(InvalidWrittenMessageException.class);
        }
    }
}
