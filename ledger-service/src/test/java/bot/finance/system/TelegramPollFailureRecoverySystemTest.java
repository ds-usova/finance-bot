package bot.finance.system;

import static bot.finance.common.TelegramTestBot.POLL_RECOVERY_TOKEN;
import static bot.finance.common.TelegramTestBot.recordedPolls;
import static bot.finance.common.TelegramTestBot.recordedPollsWithOffset;
import static bot.finance.common.TelegramTestBot.recordedSendMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.common.AbstractSystemTest;
import bot.finance.common.TelegramFixtures;
import bot.finance.common.WireMockStubs;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * The bot token below is what isolates this class: a differing property defeats Spring's context cache, so the
 * class gets its own context, a poll loop starting at offset 0, and a {@code /bot<token>/getUpdates} path no
 * other class's poller reaches.
 */
@TestPropertySource(properties = "telegram.bot.token=" + POLL_RECOVERY_TOKEN)
class TelegramPollFailureRecoverySystemTest extends AbstractSystemTest {

    private static final int UPDATE_ID = 42;
    private static final long CHAT_ID = 555L;
    private static final String CONVERSATION_ID = "555";
    private static final String MESSAGE_TEXT = "lunch 12 euro";
    private static final String CONFIRMED_OFFSET = String.valueOf(UPDATE_ID + 1);
    private static final int TOO_MANY_REQUESTS = 429;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * The order here is load-bearing: the poll loop is already running, so the catch-all and the sendMessage stub
     * must exist before the update-bearing one, or the loop consumes the update before the reply it triggers can
     * be recorded.
     */
    @BeforeEach
    void stubTelegram() {
        WireMockStubs.telegramReturnsNoUpdates(POLL_RECOVERY_TOKEN);
        WireMockStubs.telegramAcceptsSendMessage(POLL_RECOVERY_TOKEN);
        WireMockStubs.telegramFailsOnceThenReturns(
                POLL_RECOVERY_TOKEN,
                TOO_MANY_REQUESTS,
                TelegramFixtures.updatesResponse(
                        TelegramFixtures.textMessageUpdate(UPDATE_ID, CHAT_ID, CHAT_ID, MESSAGE_TEXT)));
    }

    @Nested
    @DisplayName("unhappy path - a failed getUpdates does not kill the poll loop")
    class UnhappyPath {

        @Test
        @DisplayName(
                "when the first poll fails with error code 429 - then the loop recovers and the message is still handled")
        void whenFirstPollFailsWithTooManyRequests_thenLoopRecoversAndMessageIsStillHandled() {
            // then: the message the failed poll delayed is answered, so the failure cost the user nothing
            await("a sendMessage reply is recorded once the good response arrived")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(recordedSendMessages(POLL_RECOVERY_TOKEN))
                            .as("sendMessage requests recorded for token %s", POLL_RECOVERY_TOKEN)
                            .isNotEmpty());

            assertThat(recordedSendMessages(POLL_RECOVERY_TOKEN))
                    .singleElement()
                    .satisfies(
                            reply -> assertThat(reply.formParameter("chat_id").getValues())
                                    .as("the reply goes back into the conversation the message came from")
                                    .containsExactly(CONVERSATION_ID));

            // then: the recovered poll advanced the offset, so the batch is not delivered again
            await("a follow-up getUpdates confirms the batch").atMost(TIMEOUT).untilAsserted(() -> {
                log.debug("Recorded getUpdates requests: {}", recordedPolls(POLL_RECOVERY_TOKEN));

                assertThat(recordedPollsWithOffset(POLL_RECOVERY_TOKEN, CONFIRMED_OFFSET))
                        .as("follow-up getUpdates polls carrying offset=%s", CONFIRMED_OFFSET)
                        .isNotEmpty();
            });
        }
    }
}
