package bot.finance.ai.adapter.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestOperations;

@Configuration
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
@EnableConfigurationProperties(CallerTokenProperties.class)
public class CallerTokenConfiguration {

    @Bean
    JwtDecoder jwtDecoder(CallerTokenProperties properties, RestTemplateBuilder restTemplateBuilder) {
        RestOperations restOperations = restTemplateBuilder
                .connectTimeout(properties.jwksTimeout())
                .readTimeout(properties.jwksTimeout())
                .build();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
                .restOperations(restOperations)
                .build();
        decoder.setJwtValidator(validator(properties.issuer(), properties.audience()));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new JwtIssuerValidator(issuer), new JwtAudienceValidator(audience));
    }
}
