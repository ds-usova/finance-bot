package bot.finance.ai.common.boot;

import bot.finance.ai.adapter.logging.Slf4jLoggerFactory;
import bot.finance.ai.adapter.security.CallerTokenConfiguration;
import bot.finance.ai.adapter.security.CallerTokenVerifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots {@link CallerTokenConfiguration}, {@link CallerTokenVerifier}, {@link Slf4jLoggerFactory} and the
 * {@code RestTemplateBuilder} autoconfiguration the decoder's timeout-bound client needs — no gRPC server, no
 * other adapter. The ledger's key-set endpoint is redirected to {@link WireMockUrlConfiguration}'s stub server.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@TestPropertySource(properties = "memory.enabled=true")
@SpringBootTest(classes = {CallerTokenConfiguration.class, CallerTokenVerifier.class, Slf4jLoggerFactory.class})
@ImportAutoConfiguration(RestTemplateAutoConfiguration.class)
@Import(WireMockUrlConfiguration.class)
public @interface SecurityAdapterTest {}
