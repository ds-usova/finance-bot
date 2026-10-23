package bot.finance.ai.adapter.grpc;

import bot.finance.ai.domain.value.MessageIdentity;
import io.grpc.Context;

/**
 * Puts a caller token in scope for {@link CallerTokenContext#callerToken()} during a test call. The context key
 * itself is package-private, so this is the only way a test outside this package can populate it.
 */
public final class CallerTokenTestSupport {

    private CallerTokenTestSupport() {}

    public static void withCallerToken(String token, Runnable body) {
        Context.current().withValue(CallerTokenContext.CALLER_TOKEN, token).run(body);
    }

    public static void withTurn(String token, MessageIdentity turnKey, Runnable body) {
        Context.current()
                .withValues(CallerTokenContext.CALLER_TOKEN, token, CallerTokenContext.TURN_KEY, turnKey)
                .run(body);
    }
}
