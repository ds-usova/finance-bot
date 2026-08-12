package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CdcPropertiesTest {

    @Nested
    @DisplayName("binding the recovery secret")
    class RecoverySecret {

        /**
         * The binder ignores a placeholder it cannot resolve rather than failing, so a declaration carrying no
         * default would leave the placeholder's own text behind as the secret — a value this repository
         * publishes. Reaching the refusal at all is what says the empty default resolved to blank instead.
         */
        @Test
        @DisplayName("when the environment variable is unset - then the context refuses to start")
        void whenTheEnvironmentVariableIsUnset_thenTheContextRefusesToStart() {
            new ApplicationContextRunner()
                    .withUserConfiguration(BoundProperties.class)
                    .withPropertyValues("cdc.recovery-secret=${CDC_RECOVERY_SECRET:}")
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .hasStackTraceContaining("CDC_RECOVERY_SECRET"));
        }

        @Test
        @DisplayName("when the environment variable carries a value - then it binds as the secret")
        void whenTheEnvironmentVariableCarriesAValue_thenItBindsAsTheSecret() {
            new ApplicationContextRunner()
                    .withUserConfiguration(BoundProperties.class)
                    .withPropertyValues("cdc.recovery-secret=a-configured-secret")
                    .run(context -> assertThat(
                                    context.getBean(CdcProperties.class).recoverySecret())
                            .isEqualTo("a-configured-secret"));
        }
    }

    @Configuration
    @EnableConfigurationProperties(CdcProperties.class)
    static class BoundProperties {}
}
