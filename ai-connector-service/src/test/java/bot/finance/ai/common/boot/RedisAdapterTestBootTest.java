package bot.finance.ai.common.boot;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.adapter.redis.ChangeStreamConsumer;
import bot.finance.ai.application.port.LearnMessageOutcomePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Compiling {@link RedisAdapterTest} says nothing about whether the context actually loads against the
 * containerized Redis — this boots it and autowires the consumer, asserting nothing else.
 */
@RedisAdapterTest
class RedisAdapterTestBootTest {

    @MockitoBean
    private LearnMessageOutcomePort learnMessageOutcomePort;

    @Autowired
    private ChangeStreamConsumer changeStreamConsumer;

    @Test
    @DisplayName("when the context is booted against the containerized Redis - then the consumer bean is available")
    void whenContextIsBootedAgainstContainerizedRedis_thenConsumerBeanIsAvailable() {
        assertThat(changeStreamConsumer).isNotNull();
    }
}
