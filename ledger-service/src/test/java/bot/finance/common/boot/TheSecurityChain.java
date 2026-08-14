package bot.finance.common.boot;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.adapter.security.RecoverySecretFilter;
import bot.finance.adapter.security.SecurityConfiguration;
import bot.finance.adapter.security.TokenSigningKeys;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * The real filter chains, the signing keys behind their token decoders, and the shared-secret filter the
 * management chain places ahead of the recovery endpoint. For a slice that drives an entry point through HTTP
 * and wants the security a caller actually meets.
 *
 * <p>The chains for {@code /api/**} and {@code /mcp/**} come as one, since {@code SecurityConfiguration}
 * declares both. A slice reaching only one of them still boots the other.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({
    SecurityConfiguration.class,
    RecoverySecretFilter.class,
    TokenSigningKeys.class,
    Slf4jLoggerFactory.class,
})
public @interface TheSecurityChain {}
