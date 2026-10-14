package bot.finance.adapter.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration
public class TurnOperationsSecurityConfiguration {

    private static final String TURN_SERVICE_METHODS = "bot.finance.ledger.v1.LedgerTurnService/*";

    @Bean
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor turnOperationsAuthenticationInterceptor(
            GrpcSecurity grpcSecurity, AccessTokenProperties properties, TokenSigningKeys signingKeys)
            throws Exception {
        JwtDecoder decoder = turnJwtDecoder(properties, signingKeys);
        return grpcSecurity
                .authorizeRequests(requests -> {
                    requests.methods(TURN_SERVICE_METHODS).authenticated();
                    requests.allRequests().permitAll();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(decoder)))
                .build();
    }

    private static JwtDecoder turnJwtDecoder(AccessTokenProperties properties, TokenSigningKeys signingKeys) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKeys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                SecurityConfiguration.validator(
                        new JwtTimestampValidator(properties.clockSkew()),
                        properties.issuer(),
                        properties.audience(),
                        properties.ttl()),
                messageReferenceValidator()));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> messageReferenceValidator() {
        return token -> {
            if (!StringUtils.hasText(token.getClaimAsString(AccessTokenMinter.INCOMING_MESSAGE_ID_CLAIM))) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "token carries no message reference", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }
}
