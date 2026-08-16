package bot.finance.ai.common.boot;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.adapter.persistence.IncomingMessageEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Compiling {@link PersistenceAdapterTest} says nothing about whether the context actually loads against the
 * containerized database — this boots it and autowires the repository, asserting nothing else.
 */
@PersistenceAdapterTest
class PersistenceAdapterTestBootTest {

    @Autowired
    private IncomingMessageEntityRepository repository;

    @Test
    @DisplayName("when the context is booted against the containerized database - then the repository bean is "
            + "available")
    void whenContextIsBootedAgainstContainerizedDatabase_thenRepositoryBeanIsAvailable() {
        assertThat(repository).isNotNull();
    }
}
