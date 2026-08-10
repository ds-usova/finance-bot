package bot.finance.common.fixtures;

import bot.finance.domain.value.IncomingMessageId;
import java.util.UUID;

public final class IncomingMessages {

    private IncomingMessages() {}

    /** An id no other turn in the same run shares, for a test that needs one but asserts nothing about its text. */
    public static IncomingMessageId newIncomingMessageId() {
        return IncomingMessageId.of(UUID.randomUUID().toString());
    }
}
