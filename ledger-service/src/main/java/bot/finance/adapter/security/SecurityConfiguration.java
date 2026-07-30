package bot.finance.adapter.security;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(AccessTokenProperties.class)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.requestMatchers(
                                "/actuator/**", "/.well-known/jwks.json")
                        .permitAll()
                        .requestMatchers("/mcp/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(AccessTokenProperties properties, @Value("${server.port}") int serverPort) {
        String jwkSetUri = "http://localhost:" + serverPort + "/.well-known/jwks.json";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(properties.issuer()),
                new JwtAudienceValidator(properties.audience()),
                ttlValidator(properties.ttl())));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> ttlValidator(Duration ttl) {
        return token -> {
            Instant issuedAt = token.getIssuedAt();
            Instant expiresAt = token.getExpiresAt();
            if (issuedAt == null || expiresAt == null || Duration.between(issuedAt, expiresAt).compareTo(ttl) > 0) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "token ttl exceeds the configured maximum", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }
}
