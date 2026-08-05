package bot.finance.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.MessageReference;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedCallerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("resolving the authenticated user id from the security context")
    class AuthenticatedUserIdMethod {

        @Test
        @DisplayName("when the security context holds a validated token whose subject is an external id"
                + " - then returns an AuthenticatedUserId carrying that subject")
        void whenContextHoldsValidatedTokenWithExternalIdSubject_thenReturnsAuthenticatedUserIdCarryingThatSubject() {
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtWithSubject("ext-123")));

            AuthenticatedUserId userId = AuthenticatedCaller.authenticatedUserId();

            assertThat(userId).isEqualTo(new AuthenticatedUserId("ext-123"));
        }

        @Test
        @DisplayName("when the security context holds no authentication - then throws InvalidUserException")
        void whenContextHoldsNoAuthentication_thenThrowsInvalidUserException() {
            assertThatThrownBy(AuthenticatedCaller::authenticatedUserId).isInstanceOf(InvalidUserException.class);
        }

        @Test
        @DisplayName("when the security context holds an authentication that is not a validated token"
                + " - then throws InvalidUserException")
        void whenContextHoldsAuthenticationThatIsNotAValidatedToken_thenThrowsInvalidUserException() {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken("user", "password"));

            assertThatThrownBy(AuthenticatedCaller::authenticatedUserId).isInstanceOf(InvalidUserException.class);
        }

        @Test
        @DisplayName("when the security context holds a validated token with a blank subject"
                + " - then throws InvalidUserException")
        void whenContextHoldsValidatedTokenWithBlankSubject_thenThrowsInvalidUserException() {
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtWithSubject("   ")));

            assertThatThrownBy(AuthenticatedCaller::authenticatedUserId).isInstanceOf(InvalidUserException.class);
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

    @Nested
    @DisplayName("resolving the message reference from the security context")
    class MessageReferenceMethod {

        @Test
        @DisplayName("when the security context holds a validated token whose mrf claim is a UUID's canonical text"
                + " - then returns a MessageReference carrying that UUID")
        void whenContextHoldsValidatedTokenWithUuidMrfClaim_thenReturnsMessageReferenceCarryingThatUuid() {
            UUID reference = UUID.randomUUID();
            SecurityContextHolder.getContext()
                    .setAuthentication(new JwtAuthenticationToken(jwtWithMrfClaim(reference.toString())));

            MessageReference messageReference = AuthenticatedCaller.messageReference();

            assertThat(messageReference).isEqualTo(new MessageReference(reference));
        }

        @Test
        @DisplayName("when the security context holds a validated token with no mrf claim"
                + " - then throws InvalidIncomingMessageException")
        void whenContextHoldsValidatedTokenWithNoMrfClaim_thenThrowsInvalidIncomingMessageException() {
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtWithoutMrfClaim()));

            assertThatThrownBy(AuthenticatedCaller::messageReference)
                    .isInstanceOf(InvalidIncomingMessageException.class);
        }

        @Test
        @DisplayName("when the security context holds a validated token whose mrf claim is not a UUID"
                + " - then throws InvalidIncomingMessageException")
        void whenContextHoldsValidatedTokenWithNonUuidMrfClaim_thenThrowsInvalidIncomingMessageException() {
            SecurityContextHolder.getContext()
                    .setAuthentication(new JwtAuthenticationToken(jwtWithMrfClaim("not-a-uuid")));

            assertThatThrownBy(AuthenticatedCaller::messageReference)
                    .isInstanceOf(InvalidIncomingMessageException.class);
        }

        @Test
        @DisplayName("when the security context holds no authentication, or one that is not a validated token"
                + " - then throws InvalidUserException")
        void whenContextHoldsNoValidatedToken_thenThrowsInvalidUserException() {
            assertThatThrownBy(AuthenticatedCaller::messageReference).isInstanceOf(InvalidUserException.class);

            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken("user", "password"));

            assertThatThrownBy(AuthenticatedCaller::messageReference).isInstanceOf(InvalidUserException.class);
        }

        private static Jwt jwtWithMrfClaim(String mrf) {
            return Jwt.withTokenValue("token-value")
                    .header("alg", "RS256")
                    .subject("ext-123")
                    .claim("mrf", mrf)
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();
        }

        private static Jwt jwtWithoutMrfClaim() {
            return Jwt.withTokenValue("token-value")
                    .header("alg", "RS256")
                    .subject("ext-123")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();
        }
    }
}
