package bot.finance.ai.common.boot;

import bot.finance.ai.common.containers.LedgerTurnServer;
import bot.finance.ai.common.stubs.LedgerTurnStubs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * A system test with the classifier turn on: a key and a model for the classifier, whose base URL
 * {@link AbstractSystemTest} points at the stub server, and {@code ledger.turn.target} at
 * {@link LedgerTurnServer}'s port. Resets {@link LedgerTurnStubs} before and after each test, since the fake
 * server is a JVM-wide singleton.
 */
@TestPropertySource(
        properties = {"extraction.mode=classifier", "classifier.api-key=test-key", "classifier.model=test-model"})
public abstract class AbstractClassifierSystemTest extends AbstractSystemTest {

    @DynamicPropertySource
    static void ledgerTurnProperties(DynamicPropertyRegistry registry) {
        registry.add("ledger.turn.target", LedgerTurnServer::target);
    }

    @BeforeEach
    void resetLedgerTurnStubsBefore() {
        LedgerTurnStubs.reset();
    }

    @AfterEach
    void resetLedgerTurnStubs() {
        LedgerTurnStubs.reset();
    }
}
