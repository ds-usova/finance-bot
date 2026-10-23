package bot.finance.ai.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.common.fixtures.CallerTokens;
import bot.finance.ai.domain.exception.CallerNotIdentifiedException;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnverifiedCallerClaimsTest {

    private static final String BEARER = "Bearer ";

    @Nested
    @DisplayName("read()")
    class Read {

        @Test
        @DisplayName("when the token is signed by a key no published set holds - then its identity and expiry are read")
        void whenTokenSignedByUnpublishedKey_thenIdentityAndExpiryAreRead() {
            UnverifiedCallerClaims claims =
                    UnverifiedCallerClaims.read(CallerTokens.bearerSignedByUnpublishedKey(42, "m-1"));

            assertThat(claims.identity()).isEqualTo(new MessageIdentity(42, "m-1"));
            assertThat(claims.expiry()).isPresent();
            assertThat(claims.expiry().get())
                    .isBetween(Instant.now().plusSeconds(60), Instant.now().plusSeconds(180));
        }

        @Test
        @DisplayName("when the token names an expiry - then the expiry is the token's")
        void whenTokenNamesExpiry_thenExpiryIsTheTokens() {
            Instant before = Instant.now();

            UnverifiedCallerClaims claims =
                    UnverifiedCallerClaims.read(CallerTokens.bearerExpiringIn(42, "m-1", Duration.ofMinutes(5)));

            assertThat(claims.identity()).isEqualTo(new MessageIdentity(42, "m-1"));
            assertThat(claims.expiry()).isPresent();
            assertThat(claims.expiry().get())
                    .isBetween(
                            before.plus(Duration.ofMinutes(5)).minusSeconds(1),
                            Instant.now().plus(Duration.ofMinutes(5)));
        }

        @Test
        @DisplayName("when the token is expired - then its identity and its past expiry are read")
        void whenTokenIsExpired_thenIdentityAndPastExpiryAreRead() {
            UnverifiedCallerClaims claims = UnverifiedCallerClaims.read(CallerTokens.bearerExpired(42, "m-1"));

            assertThat(claims.identity()).isEqualTo(new MessageIdentity(42, "m-1"));
            assertThat(claims.expiry()).isPresent();
            assertThat(claims.expiry().get()).isBefore(Instant.now());
        }

        @Test
        @DisplayName("when the token has no expiry - then its identity is read and the expiry is absent")
        void whenTokenHasNoExpiry_thenIdentityIsReadAndExpiryIsAbsent() {
            UnverifiedCallerClaims claims = UnverifiedCallerClaims.read(CallerTokens.bearerNoExpiry(42, "m-1"));

            assertThat(claims.identity()).isEqualTo(new MessageIdentity(42, "m-1"));
            assertThat(claims.expiry()).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("unidentifiableCallers")
        @DisplayName("when the value does not identify a caller - then it throws CallerNotIdentifiedException")
        void whenValueDoesNotIdentifyCaller_thenThrowsCallerNotIdentified(String name, String authorization) {
            assertThatThrownBy(() -> UnverifiedCallerClaims.read(authorization))
                    .isInstanceOf(CallerNotIdentifiedException.class);
        }

        static Stream<Arguments> unidentifiableCallers() {
            return Stream.of(
                    Arguments.of(
                            "no Bearer scheme", CallerTokens.bearer(42, "m-1").substring(BEARER.length())),
                    Arguments.of("Bearer value that is no JWT", BEARER + "not-a-jwt"),
                    Arguments.of("no sub", CallerTokens.bearerNoSubject("m-1")),
                    Arguments.of("non-numeric sub", CallerTokens.bearerNonNumericSubject("m-1")),
                    Arguments.of("no imi", CallerTokens.bearerNoIncomingMessageId(42)),
                    Arguments.of("blank imi", CallerTokens.bearerBlankIncomingMessageId(42)));
        }
    }
}
