package bot.finance.common.boot;

import bot.finance.LedgerServiceApplication;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Every bean the application has, on a random HTTP port, against the containerized database — for a test that
 * enters through the same doors a person does. A test of one adapter takes that adapter's own annotation
 * instead.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@OnTheContainerizedDatabase
@SpringBootTest(classes = LedgerServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public @interface TheWholeApplication {}
