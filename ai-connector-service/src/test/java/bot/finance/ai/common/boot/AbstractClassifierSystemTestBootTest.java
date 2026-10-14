package bot.finance.ai.common.boot;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.application.port.LedgerTurnPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Compiling {@link AbstractClassifierSystemTest} says nothing about whether the context actually loads with the
 * classifier turn on — this boots it through a throwaway subclass and asserts nothing else.
 */
class AbstractClassifierSystemTestBootTest extends AbstractClassifierSystemTest {

    @Autowired
    private LedgerTurnPort ledgerTurnPort;

    @Test
    @DisplayName("when the context is booted with the classifier turn on - then the ledger turn port bean is available")
    void whenContextIsBootedWithClassifierTurnOn_thenLedgerTurnPortBeanIsAvailable() {
        assertThat(ledgerTurnPort).isNotNull();
    }
}
