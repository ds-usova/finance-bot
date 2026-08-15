package bot.finance.common.boot;

import bot.finance.common.containers.PostgresContainers;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Points a context at the {@link PostgresContainers} singleton, and skips the class rather than failing it when
 * Docker is down.
 *
 * <p>It carries no {@code @ActiveProfiles}: {@link PersistenceAdapterTest} runs without the test profile today,
 * and folding one in here would start loading {@code application-test.yaml} for every persistence test class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Testcontainers(disabledWithoutDocker = true)
@ImportTestcontainers(PostgresContainers.class)
public @interface OnTheContainerizedDatabase {}
