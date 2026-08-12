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
         * default leaves the placeholder's own text behind as the secret — a value this repository publishes.
         * The empty default is what keeps an unconfigured deployment refusing every call.
         */
        @Test
        @DisplayName("when the environment variable is unset - then the secret is blank, never the placeholder text")
        void whenTheEnvironmentVariableIsUnset_thenTheSecretIsBlankRatherThanThePlaceholderText() {
            new ApplicationContextRunner()
                    .withUserConfiguration(BoundProperties.class)
                    .withPropertyValues("cdc.recovery-secret=${CDC_RECOVERY_SECRET:}")
                    .run(context -> assertThat(
                                    context.getBean(CdcProperties.class).recoverySecret())
                            .isBlank());
        }
    }

    @Configuration
    @EnableConfigurationProperties(CdcProperties.class)
    static class BoundProperties {}
}
