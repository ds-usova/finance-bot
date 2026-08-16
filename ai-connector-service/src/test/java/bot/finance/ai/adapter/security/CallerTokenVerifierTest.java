package bot.finance.ai.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.common.boot.SecurityAdapterTest;
import bot.finance.ai.common.containers.WireMockSupport;
import bot.finance.ai.common.fixtures.CallerTokens;
import bot.finance.ai.common.stubs.LedgerJwksStubs;
import bot.finance.ai.domain.exception.CallerNotIdentifiedException;
import bot.finance.ai.domain.exception.CallerVerificationUnavailableException;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

@SecurityAdapterTest
class CallerTokenVerifierTest {

    private static final long USER_ID = 42L;
    private static final String INCOMING_MESSAGE_ID = "incoming-message-1";

    @Autowired
    private CallerTokenVerifier verifier;

    @Autowired
    private CallerTokenProperties properties;

    @BeforeEach
    void setUp() {
        WireMockSupport.SERVER.resetAll();
    }

    private static Stream<Arguments> rejectedSignedTokens() {
        return Stream.of(
                Arguments.of("expired", CallerTokens.bearerExpired(USER_ID, INCOMING_MESSAGE_ID)),
                Arguments.of("wrong issuer", CallerTokens.bearerWrongIssuer(USER_ID, INCOMING_MESSAGE_ID)),
                Arguments.of("wrong audience", CallerTokens.bearerWrongAudience(USER_ID, INCOMING_MESSAGE_ID)));
    }

    private static Stream<Arguments> malformedClaimTokens() {
        return Stream.of(
                Arguments.of("no sub", CallerTokens.bearerNoSubject(INCOMING_MESSAGE_ID)),
                Arguments.of("non-numeric sub", CallerTokens.bearerNonNumericSubject(INCOMING_MESSAGE_ID)),
                Arguments.of("no imi", CallerTokens.bearerNoIncomingMessageId(USER_ID)),
                Arguments.of("blank imi", CallerTokens.bearerBlankIncomingMessageId(USER_ID)));
    }

    @Nested
    @DisplayName("verify()")
    class Verify {

        @Test
        @DisplayName("when a token minted for a user and a message is verified - then the identity carries that "
                + "user id and message id")
        void whenTokenValidForUserAndMessage_thenIdentityCarriesUserIdAndMessageId() {
            LedgerJwksStubs.stubKeySet();

            MessageIdentity identity = verifier.verify(CallerTokens.bearer(USER_ID, INCOMING_MESSAGE_ID));

            assertThat(identity.userId()).isEqualTo(USER_ID);
            assertThat(identity.incomingMessageId()).isEqualTo(INCOMING_MESSAGE_ID);
        }

        @Test
        @DisplayName("when the token is signed by a key the published set does not carry - then "
                + "CallerNotIdentifiedException is thrown")
        void whenSignedByUnpublishedKey_thenThrowsCallerNotIdentifiedException() {
            LedgerJwksStubs.stubKeySet();
            String bearer = CallerTokens.bearerSignedByUnpublishedKey(USER_ID, INCOMING_MESSAGE_ID);

            assertThatThrownBy(() -> verifier.verify(bearer)).isInstanceOf(CallerNotIdentifiedException.class);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.ai.adapter.security.CallerTokenVerifierTest#rejectedSignedTokens")
        @DisplayName("when the token is expired, names another issuer, or names another audience - then "
                + "CallerNotIdentifiedException")
        void whenTokenExpiredOrWrongIssuerOrWrongAudience_thenThrowsCallerNotIdentifiedException(
                String description, String bearer) {
            LedgerJwksStubs.stubKeySet();

            assertThatThrownBy(() -> verifier.verify(bearer)).isInstanceOf(CallerNotIdentifiedException.class);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.ai.adapter.security.CallerTokenVerifierTest#malformedClaimTokens")
        @DisplayName("when a valid token lacks sub, has non-numeric sub, lacks imi, or blank imi - then "
                + "CallerNotIdentifiedException")
        void whenValidlySignedTokenHasMissingOrInvalidClaims_thenThrowsCallerNotIdentifiedException(
                String description, String bearer) {
            LedgerJwksStubs.stubKeySet();

            assertThatThrownBy(() -> verifier.verify(bearer)).isInstanceOf(CallerNotIdentifiedException.class);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"Basic dXNlcjpwYXNz", "not-a-bearer-value-at-all", "Bearer not-a-jwt-at-all"})
        @DisplayName("when the header is not a Bearer JWT - then CallerNotIdentifiedException is thrown")
        void whenHeaderIsNotBearerJwt_thenThrowsCallerNotIdentifiedException(String authorization) {
            LedgerJwksStubs.stubKeySet();

            assertThatThrownBy(() -> verifier.verify(authorization)).isInstanceOf(CallerNotIdentifiedException.class);
        }

        @Test
        @DisplayName("when the endpoint becomes unreachable after a first verification - then the second token's "
                + "identity comes from cache")
        void whenEndpointUnreachableAfterFirstVerification_thenSecondTokenAnsweredFromCachedKeySet() {
            LedgerJwksStubs.stubKeySet();
            verifier.verify(CallerTokens.bearer(USER_ID, INCOMING_MESSAGE_ID));

            LedgerJwksStubs.stubKeySetUnreachable();
            String secondIncomingMessageId = "incoming-message-2";
            MessageIdentity identity = verifier.verify(CallerTokens.bearer(USER_ID, secondIncomingMessageId));

            assertThat(identity.userId()).isEqualTo(USER_ID);
            assertThat(identity.incomingMessageId()).isEqualTo(secondIncomingMessageId);
        }

        @Nested
        @DisplayName("verify() with no key set cached")
        @DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
        class VerifyWithNoKeySetCached {

            @Test
            @DisplayName("when the key-set endpoint fails the connection - then "
                    + "CallerVerificationUnavailableException is thrown")
            void whenKeySetEndpointUnreachableAndNoKeySetCached_thenThrowsCallerVerificationUnavailableException() {
                LedgerJwksStubs.stubKeySetUnreachable();
                String bearer = CallerTokens.bearer(USER_ID, INCOMING_MESSAGE_ID);

                assertThatThrownBy(() -> verifier.verify(bearer))
                        .isInstanceOf(CallerVerificationUnavailableException.class);
            }

            @Test
            @DisplayName("when the key-set endpoint answers slower than jwksTimeout - then "
                    + "CallerVerificationUnavailableException is thrown")
            void whenKeySetEndpointSlowerThanTimeoutAndNoKeySetCached_thenThrowsWithinTimeoutPlusMargin() {
                Duration timeout = properties.jwksTimeout();
                LedgerJwksStubs.stubKeySetDelayed(timeout.plusSeconds(5));
                String bearer = CallerTokens.bearer(USER_ID, INCOMING_MESSAGE_ID);

                Instant start = Instant.now();
                assertThatThrownBy(() -> verifier.verify(bearer))
                        .isInstanceOf(CallerVerificationUnavailableException.class);
                Duration elapsed = Duration.between(start, Instant.now());

                assertThat(elapsed).isLessThan(timeout.plus(Duration.ofSeconds(2)));
            }
        }
    }
}
