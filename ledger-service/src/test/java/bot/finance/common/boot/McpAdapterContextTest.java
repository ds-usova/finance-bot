package bot.finance.common.boot;

import bot.finance.adapter.mcp.CreateExpenseProposalMcpTool;
import bot.finance.adapter.mcp.ListCategoriesMcpTool;
import bot.finance.adapter.mcp.SummarizeSpendingMcpTool;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.application.port.ToolCallMeters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves {@link McpAdapterTest} itself boots a working context - compiling says nothing about whether the MCP
 * server, the web layer and the security chain that validates a tool call's token come up together without the
 * rest of the application. Throwaway: it asserts nothing beyond the autowiring succeeding.
 */
@McpAdapterTest
@MockitoBean(types = ToolCallMeters.class)
class McpAdapterContextTest {

    @Autowired
    private ListCategoriesMcpTool listCategoriesMcpTool;

    @Autowired
    private CreateExpenseProposalMcpTool createExpenseProposalMcpTool;

    @Autowired
    private SummarizeSpendingMcpTool summarizeSpendingMcpTool;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Test
    void contextLoads() {}
}
