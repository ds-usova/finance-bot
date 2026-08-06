package bot.finance.adapter.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Resolves the browser session token from its cookie alone. The {@code Authorization} header is deliberately
 * ignored, so a token minted for another audience cannot be presented to the session API by hand.
 *
 * <p>Never a bean: a lone {@code BearerTokenResolver} in the context is picked up by every resource-server
 * chain, which would leave the MCP endpoint reading the session cookie instead of its own header.
 */
public class SessionCookieBearerTokenResolver implements BearerTokenResolver {

    private final String cookieName;

    public SessionCookieBearerTokenResolver(WebSessionProperties properties) {
        this.cookieName = properties.cookieName();
    }

    @Override
    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
