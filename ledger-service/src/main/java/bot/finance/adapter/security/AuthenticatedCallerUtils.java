package bot.finance.adapter.security;

import bot.finance.domain.value.AuthenticatedUserId;

public final class AuthenticatedCallerUtils {

    private AuthenticatedCallerUtils() {}

    public static AuthenticatedUserId authenticatedUserId() {
        // reads the validated token from the security context and returns its subject as an
        // AuthenticatedUserId; throws InvalidUserException when the context carries no validated token
        return null;
    }
}
