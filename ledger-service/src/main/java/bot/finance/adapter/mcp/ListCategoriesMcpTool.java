package bot.finance.adapter.mcp;

import bot.finance.application.port.ListCategoriesPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ListCategoriesMcpTool {

    private final ListCategoriesPort listCategoriesPort;
    private final JsonMapper jsonMapper;
    private final Logger log;

    public ListCategoriesMcpTool(
            ListCategoriesPort listCategoriesPort, JsonMapper jsonMapper, LoggerFactory loggerFactory) {
        this.listCategoriesPort = listCategoriesPort;
        this.jsonMapper = jsonMapper;
        this.log = loggerFactory.getLogger(ListCategoriesMcpTool.class);
    }

    @McpTool(
            name = "list_categories",
            description = "Lists the categories filed under one of the caller's groupings. An expense is filed "
                    + "under one of these, never under the grouping itself.")
    public CallToolResult listCategories(
            @McpToolParam(description = "the grouping's name, exactly as it was offered") String parentCategory) {
        // logs the call at debug, reads the caller off the token, invokes ListCategoriesPort, serializes
        // ListCategoriesToolResponse, and renders every failure as an isError result logged at warn
        return null;
    }
}
