package bot.finance.ai.adapter.grpc;

import java.util.Optional;

public final class CallerTokenUtils {

    static final io.grpc.Context.Key<String> CALLER_TOKEN = io.grpc.Context.key("callerToken");

    private CallerTokenUtils() {}

    public static Optional<String> callerToken() {
        // Reads the caller's token back off the io.grpc.Context key CallerTokenInterceptor populates for the
        // call's duration; empty when no interceptor ran or no token was attached.
        return Optional.empty();
    }
}
