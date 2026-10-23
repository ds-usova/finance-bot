package bot.finance.ai.common.boot;

import bot.finance.ai.adapter.logging.Slf4jLoggerFactory;
import bot.finance.ai.adapter.typesafe.TypesafeCategoryClassifierAdapter;
import bot.finance.ai.adapter.typesafe.TypesafeClassifierConfiguration;
import bot.finance.ai.common.containers.WireMockSupport;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots {@link TypesafeCategoryClassifierAdapter} and {@link TypesafeClassifierConfiguration} with
 * {@code extraction.mode=classifier}, a key, a model and a one-second timeout — no gRPC server, no other adapter.
 * {@code classifier.base-url} is redirected to {@link WireMockSupport}'s dynamic port through
 * {@link WireMockUrlConfiguration}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@SpringBootTest(
        classes = {
            TypesafeCategoryClassifierAdapter.class,
            TypesafeClassifierConfiguration.class,
            Slf4jLoggerFactory.class
        },
        properties = {
            "extraction.mode=classifier",
            "classifier.api-key=test-key",
            "classifier.model=test-model",
            "classifier.timeout=1s"
        })
@Import(WireMockUrlConfiguration.class)
public @interface ClassifierAdapterTest {}
