package bot.finance.adapter.security;

import bot.finance.adapter.cdc.CdcProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards {@code POST /actuator/cdc} alone, ahead of Spring Security's own authorization: the request's
 * {@code X-Cdc-Recovery-Secret} header is compared to {@link CdcProperties#recoverySecret()} in constant time,
 * and answers 401 on absence or mismatch, including when no secret is configured at all. Every other path
 * passes through untouched.
 */
@Component
public class RecoverySecretFilter extends OncePerRequestFilter {

    private static final String SECRET_HEADER = "X-Cdc-Recovery-Secret";
    private static final String RECOVERY_PATH = "/actuator/cdc";

    private final CdcProperties properties;

    public RecoverySecretFilter(CdcProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!RECOVERY_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!secretMatches(request.getHeader(SECRET_HEADER))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean secretMatches(String header) {
        String secret = properties.recoverySecret();
        if (secret == null || header == null) {
            return false;
        }
        return MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
    }
}
