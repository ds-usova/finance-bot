package bot.finance.adapter.security;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class AuthenticatedCallerUtils {

    private AuthenticatedCallerUtils() {}

    public static AuthenticatedUserId authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new InvalidUserException("security context does not hold a validated token");
        }
        return new AuthenticatedUserId(jwtAuthenticationToken.getToken().getSubject());
    }
}
