package bot.finance.ai.common.boot;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.application.port.RecallMeters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Compiling {@link MetricsAdapterTest} says nothing about whether the context actually loads with the actuator
 * endpoint up — this boots it and autowires one meters bean, asserting nothing else.
 */
@MetricsAdapterTest
class MetricsAdapterContextTest {

    @Autowired
    private RecallMeters recallMeters;

    @Test
    @DisplayName("when the context is booted - then the recall meters bean is available")
    void whenContextIsBooted_thenRecallMetersBeanIsAvailable() {
        assertThat(recallMeters).isNotNull();
    }
}
