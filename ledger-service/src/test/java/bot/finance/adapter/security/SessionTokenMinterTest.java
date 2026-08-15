package bot.finance.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.fixtures.SessionTokens;
import bot.finance.common.fixtures.SigningKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SessionTokenMinterTest {

    private static final long USER_ID = 42L;

    private final SessionTokenProperties properties = SessionTokens.properties();

    private final SessionTokenMinter minter = SessionTokens.minter();

    @Nested
    @DisplayName("minting a session token")
    class Mint {

        @Test
        @DisplayName("when mint() is called - then the token carries the configured subject, issuer, audience "
                + "and a jti")
        void whenMintIsCalledAndTheTokenIsParsed_thenItCarriesTheExpectedClaims() throws ParseException {
            JWTClaimsSet claims = mintedClaims();

            assertThat(claims.getSubject()).isEqualTo(Long.toString(USER_ID));
            assertThat(claims.getIssuer()).isEqualTo(properties.issuer());
            assertThat(claims.getAudience()).containsExactly(properties.audience());
            assertThat(claims.getJWTID()).isNotBlank();
        }

        @Test
        @DisplayName("when mint() is called - then the token expires the configured ttl after it was issued")
        void whenMintIsCalled_thenTheTokenExpiresTheConfiguredTtlAfterItWasIssued() throws ParseException {
            JWTClaimsSet claims = mintedClaims();

            Instant issuedAt = claims.getIssueTime().toInstant();
            Instant expiresAt = claims.getExpirationTime().toInstant();
            assertThat(Duration.between(issuedAt, expiresAt)).isEqualTo(properties.ttl());
        }

        private JWTClaimsSet mintedClaims() throws ParseException {
            return SignedJWT.parse(minter.mint(USER_ID)).getJWTClaimsSet();
        }

        @Test
        @DisplayName("when the minted token is parsed - then it carries no mrf claim")
        void whenTheMintedTokenIsParsed_thenItCarriesNoMrfClaim() throws ParseException {
            String token = minter.mint(USER_ID);

            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

            assertThat(claims.getClaim("mrf")).isNull();
        }

        @Test
        @DisplayName("when the minted token is parsed - then its audience is not the audience an MCP token carries")
        void whenTheMintedTokenIsParsed_thenItsAudienceIsNotTheAudienceAnMcpTokenCarries() throws ParseException {
            String token = minter.mint(USER_ID);

            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

            assertThat(claims.getAudience()).doesNotContain("mcp-adapter");
        }

        @Test
        @DisplayName(
                "when mint() is called twice for the same user id - then the two tokens carry different jti values")
        void whenMintIsCalledTwiceForTheSameUserId_thenTheTwoTokensCarryDifferentJtiValues() throws ParseException {
            String firstJti =
                    SignedJWT.parse(minter.mint(USER_ID)).getJWTClaimsSet().getJWTID();
            String secondJti =
                    SignedJWT.parse(minter.mint(USER_ID)).getJWTClaimsSet().getJWTID();

            assertThat(firstJti).isNotEqualTo(secondJti);
        }
    }

    @Nested
    @DisplayName("signing a session token")
    class Sign {

        @Test
        @DisplayName("when the minted token's header is read - then the algorithm is RS256 and the signature verifies")
        void whenTheMintedTokenHeaderIsRead_thenTheAlgorithmIsRs256AndTheSignatureVerifies() throws Exception {
            String token = minter.mint(USER_ID);

            SignedJWT signedJwt = SignedJWT.parse(token);

            assertThat(signedJwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
            assertThat(signedJwt.verify(new RSASSAVerifier(SigningKeys.keys().publicKey())))
                    .isTrue();
        }

        @Test
        @DisplayName("when the minted token's header is read - then its kid is the alias the JWK Set publishes")
        void whenTheMintedTokenHeaderIsRead_thenItsKidIsTheAliasTheJwkSetPublishes() throws ParseException {
            String token = minter.mint(USER_ID);

            assertThat(SignedJWT.parse(token).getHeader().getKeyID()).isEqualTo(SigningKeys.KEY_ALIAS);
        }
    }
}
