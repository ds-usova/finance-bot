package bot.finance.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.fixtures.McpTokens;
import bot.finance.domain.value.MessageReference;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyStore;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class AccessTokenMinterTest {

    private static final String USER_EXTERNAL_ID = "user-external-id-42";

    private final AccessTokenProperties properties = McpTokens.properties();

    private final AccessTokenMinter minter = new AccessTokenMinter(properties, new DefaultResourceLoader());

    @Nested
    @DisplayName("minting a token")
    class Mint {

        @Test
        @DisplayName(
                "when mint() is called and the token is parsed - then it carries sub, iss, aud, iat, exp at the configured ttl after iat, and a jti")
        void whenMintIsCalledAndTheTokenIsParsed_thenItCarriesTheExpectedClaims() throws ParseException {
            String token = minter.mint(USER_EXTERNAL_ID, MessageReference.newReference());

            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

            assertThat(claims.getSubject()).isEqualTo(USER_EXTERNAL_ID);
            assertThat(claims.getIssuer()).isEqualTo(properties.issuer());
            assertThat(claims.getAudience()).containsExactly(properties.audience());
            assertThat(claims.getJWTID()).isNotBlank();
            Instant issuedAt = claims.getIssueTime().toInstant();
            Instant expiresAt = claims.getExpirationTime().toInstant();
            assertThat(Duration.between(issuedAt, expiresAt)).isEqualTo(properties.ttl());
        }

        @Test
        @DisplayName(
                "when mint() is called twice for the same external id - then the two tokens carry different jti values")
        void whenMintIsCalledTwiceForTheSameExternalId_thenTheTwoTokensCarryDifferentJtiValues() throws ParseException {
            String firstToken = minter.mint(USER_EXTERNAL_ID, MessageReference.newReference());
            String secondToken = minter.mint(USER_EXTERNAL_ID, MessageReference.newReference());

            String firstJti = SignedJWT.parse(firstToken).getJWTClaimsSet().getJWTID();
            String secondJti = SignedJWT.parse(secondToken).getJWTClaimsSet().getJWTID();

            assertThat(firstJti).isNotEqualTo(secondJti);
        }

        @Test
        @DisplayName(
                "when mint() is called and the token's header is read - then the algorithm is RS256 and the signature verifies against the keystore's public key")
        void whenMintIsCalledAndTheTokenHeaderIsRead_thenTheAlgorithmIsRs256AndTheSignatureVerifies() throws Exception {
            String token = minter.mint(USER_EXTERNAL_ID, MessageReference.newReference());

            SignedJWT signedJwt = SignedJWT.parse(token);

            assertThat(signedJwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
            assertThat(signedJwt.verify(new RSASSAVerifier(minter.publicKey()))).isTrue();
        }

        @Test
        @DisplayName(
                "when the minted token is parsed - then its mrf claim is that reference's UUID in canonical text form")
        void whenTheMintedTokenIsParsed_thenItsMrfClaimIsThatReferencesUuidInCanonicalTextForm() throws ParseException {
            MessageReference reference = MessageReference.newReference();

            String token = minter.mint(USER_EXTERNAL_ID, reference);

            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
            assertThat(claims.getStringClaim("mrf")).isEqualTo(reference.value().toString());
        }

        @Test
        @DisplayName("when both tokens are parsed - then their mrf claims differ")
        void whenBothTokensAreParsed_thenTheirMrfClaimsDiffer() throws ParseException {
            String firstToken = minter.mint(USER_EXTERNAL_ID, MessageReference.newReference());
            String secondToken = minter.mint(USER_EXTERNAL_ID, MessageReference.newReference());

            String firstMrf = SignedJWT.parse(firstToken).getJWTClaimsSet().getStringClaim("mrf");
            String secondMrf = SignedJWT.parse(secondToken).getJWTClaimsSet().getStringClaim("mrf");

            assertThat(firstMrf).isNotEqualTo(secondMrf);
        }
    }

    @Nested
    @DisplayName("reading the public key")
    class PublicKey {

        @Test
        @DisplayName(
                "when publicKey() is called - then it returns the RSA public key matching the private key mint() signs with")
        void whenPublicKeyIsCalled_thenItReturnsTheRsaPublicKeyMatchingThePrivateKeyMintSignsWith() throws Exception {
            RSAPublicKey expectedPublicKey = loadPublicKeyDirectlyFromKeystore(properties, new DefaultResourceLoader());

            assertThat(minter.publicKey()).isEqualTo(expectedPublicKey);
        }

        private static RSAPublicKey loadPublicKeyDirectlyFromKeystore(
                AccessTokenProperties properties, ResourceLoader resourceLoader) throws Exception {
            char[] password = properties.keystorePassword().toCharArray();
            Resource resource = resourceLoader.getResource(properties.keystore());
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (var in = resource.getInputStream()) {
                keyStore.load(in, password);
            }
            return (RSAPublicKey) keyStore.getCertificate(properties.keyAlias()).getPublicKey();
        }
    }
}
