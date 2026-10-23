package bot.finance.ai.common.boot;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.application.port.CategoryClassifierPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Compiling {@link ClassifierAdapterTest} says nothing about whether the context actually loads — this boots it
 * through a throwaway class and asserts nothing else.
 */
@ClassifierAdapterTest
class ClassifierAdapterTestBootTest {

    @Autowired
    private CategoryClassifierPort categoryClassifierPort;

    @Test
    @DisplayName("when the context is booted - then the classifier port bean is available")
    void whenContextIsBooted_thenClassifierPortBeanIsAvailable() {
        assertThat(categoryClassifierPort).isNotNull();
    }
}
