package bot.finance.ai.common.boot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Compiling {@link AbstractMemorySystemTest} says nothing about whether the context actually loads with the
 * memory on against the containerized database — this boots it through a throwaway subclass and asserts nothing
 * else.
 */
class AbstractMemorySystemTestBootTest extends AbstractMemorySystemTest {

    @Test
    @DisplayName("when the context is booted with the memory on - then the JdbcTemplate bean is available")
    void whenContextIsBootedWithMemoryOn_thenJdbcTemplateBeanIsAvailable() {
        assertThat(jdbcTemplate).isNotNull();
    }
}
