package bot.finance.adapter.security;

import static bot.finance.common.fixtures.IncomingMessages.newIncomingMessageId;
import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.fixtures.SigningKeys;
import bot.finance.domain.value.IncomingMessageId;
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

class AccessTokenMinterTest {

    private static final String USER_EXTERNAL_ID = "user-external-id-42";

    private final AccessTokenProperties properties = McpTokens.properties();

    private final AccessTokenMinter minter = McpTokens.minter();

    @Nested
    @DisplayName("minting a token")
    class Mint {

        @Test
        @DisplayName("when mint() is called - then the token carries the configured subject, issuer, audience "
                + "and a jti")
        void whenMintIsCalledAndTheTokenIsParsed_thenItCarriesTheExpectedClaims() throws ParseException {
            JWTClaimsSet claims = mintedClaims();

            assertThat(claims.getSubject()).isEqualTo(USER_EXTERNAL_ID);
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
            String token = minter.mint(USER_EXTERNAL_ID, newIncomingMessageId());
            return SignedJWT.parse(token).getJWTClaimsSet();
        }

        @Test
        @DisplayName(
                "when mint() is called twice for the same external id - then the two tokens carry different jti values")
        void whenMintIsCalledTwiceForTheSameExternalId_thenTheTwoTokensCarryDifferentJtiValues() throws ParseException {
            String firstToken = minter.mint(USER_EXTERNAL_ID, newIncomingMessageId());
            String secondToken = minter.mint(USER_EXTERNAL_ID, newIncomingMessageId());

            String firstJti = SignedJWT.parse(firstToken).getJWTClaimsSet().getJWTID();
            String secondJti = SignedJWT.parse(secondToken).getJWTClaimsSet().getJWTID();

            assertThat(firstJti).isNotEqualTo(secondJti);
        }

        @Test
        @DisplayName("when mint() is called - then the token is signed RS256 and verifies against the keystore's "
                + "public key")
        void whenMintIsCalledAndTheTokenHeaderIsRead_thenTheAlgorithmIsRs256AndTheSignatureVerifies() throws Exception {
            String token = minter.mint(USER_EXTERNAL_ID, newIncomingMessageId());

            SignedJWT signedJwt = SignedJWT.parse(token);

            assertThat(signedJwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
            assertThat(signedJwt.verify(new RSASSAVerifier(SigningKeys.keys().publicKey())))
                    .isTrue();
        }

        @Test
        @DisplayName("when the minted token's header is read - then its kid is the alias the JWK Set publishes")
        void whenTheMintedTokenHeaderIsRead_thenItsKidIsTheAliasTheJwkSetPublishes() throws ParseException {
            String token = minter.mint(USER_EXTERNAL_ID, newIncomingMessageId());

            assertThat(SignedJWT.parse(token).getHeader().getKeyID()).isEqualTo(SigningKeys.KEY_ALIAS);
        }

        @Test
        @DisplayName("when the minted token is parsed - then its imi claim is that reference's value")
        void whenTheMintedTokenIsParsed_thenItsImiClaimIsThatReferencesValue() throws ParseException {
            IncomingMessageId reference = newIncomingMessageId();

            String token = minter.mint(USER_EXTERNAL_ID, reference);

            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
            assertThat(claims.getStringClaim("imi")).isEqualTo(reference.value());
        }

        @Test
        @DisplayName("when both tokens are parsed - then their imi claims differ")
        void whenBothTokensAreParsed_thenTheirImiClaimsDiffer() throws ParseException {
            String firstToken = minter.mint(USER_EXTERNAL_ID, newIncomingMessageId());
            String secondToken = minter.mint(USER_EXTERNAL_ID, newIncomingMessageId());

            String firstImi = SignedJWT.parse(firstToken).getJWTClaimsSet().getStringClaim("imi");
            String secondImi = SignedJWT.parse(secondToken).getJWTClaimsSet().getStringClaim("imi");

            assertThat(firstImi).isNotEqualTo(secondImi);
        }
    }
}
