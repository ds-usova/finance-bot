package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.config.ExtractionProperties;
import bot.finance.ai.application.dto.TurnStart;
import bot.finance.ai.application.port.TurnRunnerPort;
import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.TurnEnd;
import jakarta.annotation.PreDestroy;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class GrpcTurnRunnerAdapter implements TurnRunnerPort {

    private final ExtractionProperties properties;

    public GrpcTurnRunnerAdapter(ExtractionProperties properties) {
        this.properties = properties;
    }

    @Override
    public TurnStart start(MessageIdentity turnKey, Supplier<TurnEnd> turn, Consumer<TurnEnd> end) {
        // stub-intent: answers BUSY once the runner is closed; answers ALREADY_RUNNING while a turn under the key is in
        // flight, whatever the cap; then BUSY when the cap of turns in flight is reached; otherwise runs the turn on a
        // thread of its own in a fork of the current gRPC context, interrupting it at the turn timeout; once its thread
        // returns, hands end the turn's answer, or UNAVAILABLE past the bound, and only then frees the key and the slot
        return null;
    }

    @PreDestroy
    public void close() {
        // stub-intent: stops taking turns and waits for the turns in flight, up to the turn timeout
    }
}
