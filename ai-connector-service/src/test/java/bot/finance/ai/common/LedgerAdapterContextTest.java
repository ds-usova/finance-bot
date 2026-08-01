package bot.finance.ai.common;

import bot.finance.ai.adapter.ledger.McpExpenseProposalAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves {@link LedgerAdapterTest} boots on its own — the context loading, not a scenario. Shared test
 * infrastructure that only proves itself at runtime ships with a test that boots it.
 */
@LedgerAdapterTest
class LedgerAdapterContextTest {

    @Autowired
    private McpExpenseProposalAdapter adapter;

    @Test
    void contextLoads() {
        // Asserts nothing: a context that fails to load fails this test, which is the whole point.
    }
}
