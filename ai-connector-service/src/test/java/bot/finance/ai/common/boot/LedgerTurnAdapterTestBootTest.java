package bot.finance.ai.common.boot;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.application.port.LedgerTurnPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Compiling {@link LedgerTurnAdapterTest} says nothing about whether the context actually loads — this boots it
 * through a throwaway class and asserts nothing else.
 */
@LedgerTurnAdapterTest
class LedgerTurnAdapterTestBootTest {

    @Autowired
    private LedgerTurnPort ledgerTurnPort;

    @Test
    @DisplayName("when the context is booted - then the ledger turn port bean is available")
    void whenContextIsBooted_thenLedgerTurnPortBeanIsAvailable() {
        assertThat(ledgerTurnPort).isNotNull();
    }
}
