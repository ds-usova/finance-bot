package bot.finance.ai.adapter.grpc;

import bot.finance.ai.domain.value.MessageIdentity;
import io.grpc.Context;
import java.util.Optional;

public final class CallerTokenContext {

    static final Context.Key<String> CALLER_TOKEN = Context.key("callerToken");
    static final Context.Key<MessageIdentity> MESSAGE_IDENTITY = Context.key("messageIdentity");

    private CallerTokenContext() {}

    public static Optional<String> callerToken() {
        return Optional.ofNullable(CALLER_TOKEN.get());
    }

    public static Optional<MessageIdentity> messageIdentity() {
        return Optional.ofNullable(MESSAGE_IDENTITY.get());
    }
}
