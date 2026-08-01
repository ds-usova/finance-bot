package bot.finance.ai.adapter.grpc;

import java.util.Optional;

public final class CallerTokenUtils {

    static final io.grpc.Context.Key<String> CALLER_TOKEN = io.grpc.Context.key("callerToken");

    private CallerTokenUtils() {}

    public static Optional<String> callerToken() {
        return Optional.ofNullable(CALLER_TOKEN.get());
    }
}
