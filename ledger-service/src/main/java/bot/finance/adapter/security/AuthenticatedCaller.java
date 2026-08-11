package bot.finance.adapter.security;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class AuthenticatedCaller {

    private AuthenticatedCaller() {}

    public static AuthenticatedUserId authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new InvalidUserException("security context does not hold a validated token");
        }
        return AuthenticatedUserId.of(jwtAuthenticationToken.getToken().getSubject());
    }

    public static IncomingMessageId incomingMessageId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new InvalidUserException("security context does not hold a validated token");
        }
        String reference =
                jwtAuthenticationToken.getToken().getClaimAsString(AccessTokenMinter.INCOMING_MESSAGE_ID_CLAIM);
        return IncomingMessageId.of(reference);
    }
}
