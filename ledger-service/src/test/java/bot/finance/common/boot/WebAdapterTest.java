package bot.finance.common.boot;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.adapter.security.SecurityConfiguration;
import bot.finance.adapter.security.SessionTokenMinter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The {@code @ActiveProfiles}/{@code @Import} block a {@code @WebMvcTest} slice needs to boot the security
 * filter chain, the signing keys and the session token minter - what {@code SessionControllerTest} wrote out by
 * hand before this existed. {@code @WebMvcTest} stays on each test class, since it names the controller under
 * test.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@Import({SecurityConfiguration.class, SigningKeysConfiguration.class, SessionTokenMinter.class, Slf4jLoggerFactory.class
})
public @interface WebAdapterTest {}
