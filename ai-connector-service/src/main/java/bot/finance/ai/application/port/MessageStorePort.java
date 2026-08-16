package bot.finance.ai.application.port;

import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Instant;

public interface MessageStorePort {

    void register(MessageIdentity identity, String text);

    int deleteReceivedBefore(Instant cut, int batch);
}
