package bot.finance.ai.common.boot;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.adapter.security.CallerTokenVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Compiling {@link SecurityAdapterTest} says nothing about whether the context actually loads the JWT decoder and
 * its validators — this boots it and autowires the verifier, asserting nothing else.
 */
@SecurityAdapterTest
class SecurityAdapterTestBootTest {

    @Autowired
    private CallerTokenVerifier callerTokenVerifier;

    @Test
    @DisplayName("when the context is booted - then the caller token verifier bean is available")
    void whenContextIsBooted_thenCallerTokenVerifierBeanIsAvailable() {
        assertThat(callerTokenVerifier).isNotNull();
    }
}
