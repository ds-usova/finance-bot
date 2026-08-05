package bot.finance.ai.adapter.grpc;

import io.grpc.Context;
import java.util.Optional;

public final class CallerTokenContext {

    static final Context.Key<String> CALLER_TOKEN = Context.key("callerToken");

    private CallerTokenContext() {}

    public static Optional<String> callerToken() {
        return Optional.ofNullable(CALLER_TOKEN.get());
    }
}
