package bot.finance.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Instant;
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
        @DisplayName("when the token's subject is an external id - then returns an AuthenticatedUserId carrying "
                + "that subject")
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
    @DisplayName("resolving the incoming message id from the security context")
    class IncomingMessageIdMethod {

        @Test
        @DisplayName(
                "when the token's imi claim is present" + " - then returns an IncomingMessageId carrying that value")
        void whenContextHoldsValidatedTokenWithImiClaim_thenReturnsIncomingMessageIdCarryingThatValue() {
            String reference = "conversation-1:42";
            SecurityContextHolder.getContext()
                    .setAuthentication(new JwtAuthenticationToken(jwtWithImiClaim(reference)));

            IncomingMessageId incomingMessageId = AuthenticatedCaller.incomingMessageId();

            assertThat(incomingMessageId).isEqualTo(new IncomingMessageId(reference));
        }

        @Test
        @DisplayName("when the security context holds a validated token with no imi claim"
                + " - then throws InvalidIncomingMessageException")
        void whenContextHoldsValidatedTokenWithNoImiClaim_thenThrowsInvalidIncomingMessageException() {
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtWithoutImiClaim()));

            assertThatThrownBy(AuthenticatedCaller::incomingMessageId)
                    .isInstanceOf(InvalidIncomingMessageException.class);
        }

        @Test
        @DisplayName("when the token's imi claim is blank - then throws InvalidIncomingMessageException")
        void whenContextHoldsValidatedTokenWithBlankImiClaim_thenThrowsInvalidIncomingMessageException() {
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtWithImiClaim("   ")));

            assertThatThrownBy(AuthenticatedCaller::incomingMessageId)
                    .isInstanceOf(InvalidIncomingMessageException.class);
        }

        @Test
        @DisplayName("when the security context holds no authentication - then throws InvalidUserException")
        void whenContextHoldsNoAuthentication_thenThrowsInvalidUserException() {
            assertThatThrownBy(AuthenticatedCaller::incomingMessageId).isInstanceOf(InvalidUserException.class);
        }

        @Test
        @DisplayName("when the security context holds an authentication that is not a validated token"
                + " - then throws InvalidUserException")
        void whenContextHoldsAuthenticationThatIsNotAValidatedToken_thenThrowsInvalidUserException() {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken("user", "password"));

            assertThatThrownBy(AuthenticatedCaller::incomingMessageId).isInstanceOf(InvalidUserException.class);
        }

        private static Jwt jwtWithImiClaim(String imi) {
            return Jwt.withTokenValue("token-value")
                    .header("alg", "RS256")
                    .subject("ext-123")
                    .claim("imi", imi)
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();
        }

        private static Jwt jwtWithoutImiClaim() {
            return Jwt.withTokenValue("token-value")
                    .header("alg", "RS256")
                    .subject("ext-123")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();
        }
    }
}
