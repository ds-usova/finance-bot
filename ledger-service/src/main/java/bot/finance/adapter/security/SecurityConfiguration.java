package bot.finance.adapter.security;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.util.function.SingletonSupplier;

@Configuration
@EnableConfigurationProperties({
    AccessTokenProperties.class,
    TokenSigningProperties.class,
    SessionTokenProperties.class,
    WebSessionProperties.class
})
public class SecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain webSessionSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("sessionJwtDecoder") JwtDecoder sessionJwtDecoder,
            WebSessionProperties cookieProperties) {
        BearerTokenResolver sessionCookieResolver = new SessionCookieBearerTokenResolver(cookieProperties);
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(eagerCsrfTokenRequestHandler()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/session")
                        .permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/session")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/session")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/expenses")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/groupings")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 ->
                        oauth2.bearerTokenResolver(sessionCookieResolver).jwt(jwt -> jwt.decoder(sessionJwtDecoder)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, @Qualifier("mcpJwtDecoder") JwtDecoder jwtDecoder) {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/**", "/.well-known/jwks.json")
                        .permitAll()
                        .requestMatchers("/mcp/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));
        return http.build();
    }

    @Bean("mcpJwtDecoder")
    JwtDecoder mcpJwtDecoder(AccessTokenProperties properties, Environment environment) {
        // The actual bound port is only published as local.server.port once the embedded server has started,
        // which happens after this bean is eagerly instantiated under a random-port test. Resolving it lazily,
        // on first decode, lets the JWKS route still resolve to this service's own listening port.
        SingletonSupplier<JwtDecoder> delegate =
                SingletonSupplier.of(() -> buildMcpJwtDecoder(properties, environment));
        return token -> delegate.obtain().decode(token);
    }

    @Bean("sessionJwtDecoder")
    JwtDecoder sessionJwtDecoder(SessionTokenProperties properties, TokenSigningKeys signingKeys) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKeys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(properties.issuer()),
                new JwtAudienceValidator(properties.audience()),
                maxLifetimeValidator(properties.ttl())));
        return decoder;
    }

    /**
     * Opting out of deferred loading writes the CSRF cookie on every request through the chain, including the
     * unauthenticated one a page makes on load, so a browser always has a token before its first write.
     */
    private static CsrfTokenRequestAttributeHandler eagerCsrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    private static JwtDecoder buildMcpJwtDecoder(AccessTokenProperties properties, Environment environment) {
        int serverPort = environment.getProperty(
                "local.server.port", Integer.class, environment.getProperty("server.port", Integer.class, 0));
        String jwkSetUri = "http://localhost:" + serverPort + "/.well-known/jwks.json";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(properties.issuer()),
                new JwtAudienceValidator(properties.audience()),
                maxLifetimeValidator(properties.ttl())));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> maxLifetimeValidator(Duration ttl) {
        return token -> {
            Instant issuedAt = token.getIssuedAt();
            Instant expiresAt = token.getExpiresAt();
            if (issuedAt == null
                    || expiresAt == null
                    || Duration.between(issuedAt, expiresAt).compareTo(ttl) > 0) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "token ttl exceeds the configured maximum", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }
}
