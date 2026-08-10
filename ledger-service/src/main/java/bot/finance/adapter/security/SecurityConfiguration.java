package bot.finance.adapter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableConfigurationProperties({
    AccessTokenProperties.class,
    TokenSigningProperties.class,
    SessionTokenProperties.class,
    WebSessionProperties.class
})
public class SecurityConfiguration {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "TRACE", "OPTIONS");

    @Bean
    @Order(1)
    SecurityFilterChain webSessionSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("sessionJwtDecoder") JwtDecoder sessionJwtDecoder,
            WebSessionProperties cookieProperties) {
        BearerTokenResolver sessionCookieResolver = new SessionCookieBearerTokenResolver(cookieProperties);
        http.securityMatcher("/api/**")
                // The built-in csrf() DSL, combined with oauth2ResourceServer() below, exempts any request its
                // bearer-token resolver recognizes from CSRF — correct for a token read off the Authorization
                // header, which a browser never attaches on its own, but wrong here: the resolver reads the
                // session cookie, exactly what a forged cross-site request also carries automatically, and that
                // exemption cannot be un-registered through the DSL. So csrf() is disabled and the two filters
                // below stand in for it; each one documents the position it is given.
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(csrfTokenIssuingFilter(), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(csrfEnforcementFilter(), AuthorizationFilter.class)
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
                        .requestMatchers(HttpMethod.POST, "/api/v1/expenses/acceptances")
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
        // Scoped to exactly the paths this chain owns, unlike the web-session chain above: with no securityMatcher
        // at all, this chain's anyRequest().denyAll() would also claim the container's forward to /error for a
        // request the other chain already refused, turning that chain's computed status into a 500 or 401 of its
        // own instead of letting the forward through to Boot's error handling.
        http.securityMatcher("/actuator/**", "/.well-known/jwks.json", "/mcp/**")
                .csrf(AbstractHttpConfigurer::disable)
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
        decoder.setJwtValidator(validator(properties.issuer(), properties.audience(), properties.ttl()));
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

    /**
     * Issues the CSRF cookie early — before authorization runs — for a safe method only, so a page's unauthenticated
     * read still gets a token even when the endpoint it reads goes on to answer 401. An unsafe method is left to
     * {@link #csrfEnforcementFilter()} alone: that filter both issues and enforces in one pass, exactly as a plain
     * {@code CsrfFilter} normally does, and running this filter for an unsafe method too would issue a second,
     * genuine cookie ahead of it — including for a request a test builds with {@code SecurityMockMvcRequestPostProcessors.csrf()},
     * which can only patch the one real {@link CsrfFilter} it finds in the chain, not this one. A dedicated {@link
     * OncePerRequestFilter} rather than a second {@code CsrfFilter} running the unconditional half of its own
     * logic, because {@code OncePerRequestFilter} guards against running twice in one request through an attribute
     * keyed on {@code getClass()}: a second plain {@code CsrfFilter} instance later in the same chain would read
     * that guard as already tripped by the first and skip its own turn — the one meant to enforce the token —
     * entirely.
     */
    private static OncePerRequestFilter csrfTokenIssuingFilter() {
        CsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestHandler requestHandler = eagerCsrfTokenRequestHandler();
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                if (isSafeMethod(request)) {
                    DeferredCsrfToken deferredCsrfToken = repository.loadDeferredToken(request, response);
                    requestHandler.handle(request, response, deferredCsrfToken::get);
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    private static boolean isSafeMethod(HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod());
    }

    /**
     * Enforces the CSRF check, positioned after {@link AuthorizationFilter} so it only ever runs once a request
     * has already cleared authorization — a {@code permitAll} path, or an authenticated one. A request authorization
     * itself refuses (no session cookie at all, on a path that requires one) never reaches this filter: that
     * refusal surfaces as 401 through the ordinary authentication-entry-point path instead. So a token missing
     * here always means the same thing and always answers 403, whoever sent the request.
     */
    private static CsrfFilter csrfEnforcementFilter() {
        CsrfFilter filter = new CsrfFilter(CookieCsrfTokenRepository.withHttpOnlyFalse());
        filter.setRequestHandler(eagerCsrfTokenRequestHandler());
        filter.setAccessDeniedHandler(new AccessDeniedHandlerImpl());
        return filter;
    }

    private static JwtDecoder buildMcpJwtDecoder(AccessTokenProperties properties, Environment environment) {
        int serverPort = environment.getProperty(
                "local.server.port", Integer.class, environment.getProperty("server.port", Integer.class, 0));
        String jwkSetUri = "http://localhost:" + serverPort + "/.well-known/jwks.json";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validator(properties.issuer(), properties.audience(), properties.ttl()));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> validator(String issuer, String audience, Duration ttl) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer),
                new JwtAudienceValidator(audience),
                maxLifetimeValidator(ttl));
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
