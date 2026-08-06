package bot.finance.common.boot;

import bot.finance.adapter.mcp.CreateExpenseProposalMcpTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves {@link McpAdapterTest} itself boots a working context - compiling says nothing about whether the
 * datasource wiring and the MCP tool bean actually come up. Throwaway: it asserts nothing beyond the
 * autowiring succeeding.
 */
@McpAdapterTest
class McpAdapterContextTest {

    @Autowired
    private CreateExpenseProposalMcpTool createExpenseProposalMcpTool;

    @Test
    void contextLoads() {}
}
