package bot.finance.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedCallerUtilsTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("resolving the authenticated user id from the security context")
    class AuthenticatedUserIdMethod {

        @Test
        @DisplayName(
                "when the security context holds a validated token whose subject is an external id"
                        + " - then returns an AuthenticatedUserId carrying that subject")
        void whenContextHoldsValidatedTokenWithExternalIdSubject_thenReturnsAuthenticatedUserIdCarryingThatSubject() {
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtWithSubject("ext-123")));

            AuthenticatedUserId userId = AuthenticatedCallerUtils.authenticatedUserId();

            assertThat(userId).isEqualTo(new AuthenticatedUserId("ext-123"));
        }

        @Test
        @DisplayName("when the security context holds no authentication - then throws InvalidUserException")
        void whenContextHoldsNoAuthentication_thenThrowsInvalidUserException() {
            assertThatThrownBy(AuthenticatedCallerUtils::authenticatedUserId).isInstanceOf(InvalidUserException.class);
        }

        @Test
        @DisplayName(
                "when the security context holds an authentication that is not a validated token"
                        + " - then throws InvalidUserException")
        void whenContextHoldsAuthenticationThatIsNotAValidatedToken_thenThrowsInvalidUserException() {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken("user", "password"));

            assertThatThrownBy(AuthenticatedCallerUtils::authenticatedUserId).isInstanceOf(InvalidUserException.class);
        }

        @Test
        @DisplayName(
                "when the security context holds a validated token with a blank subject"
                        + " - then throws InvalidUserException")
        void whenContextHoldsValidatedTokenWithBlankSubject_thenThrowsInvalidUserException() {
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtWithSubject("   ")));

            assertThatThrownBy(AuthenticatedCallerUtils::authenticatedUserId).isInstanceOf(InvalidUserException.class);
        }

        private static Jwt jwtWithSubject(String subject) {
            return Jwt.withTokenValue("token-value")
                    .header("alg", "RS256")
                    .subject(subject)
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();
        }
    }
}
