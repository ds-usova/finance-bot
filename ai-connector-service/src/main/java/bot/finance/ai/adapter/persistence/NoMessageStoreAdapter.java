package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "false")
public class NoMessageStoreAdapter implements MessageStorePort {

    @Override
    public void register(MessageIdentity identity, String text) {}

    @Override
    public int deleteReceivedBefore(Instant cut, int batch) {
        return 0;
    }
}
