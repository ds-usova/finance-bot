package bot.finance.adapter.mcp;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.application.dto.ListCategoriesCommand;
import bot.finance.application.port.ListCategoriesPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.ToolCallMeters;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.AuthenticatedUserId;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ListCategoriesMcpTool {

    private final ListCategoriesPort listCategoriesPort;
    private final JsonMapper jsonMapper;
    private final ToolCallMeters toolCallMeters;
    private final Logger log;

    public ListCategoriesMcpTool(
            ListCategoriesPort listCategoriesPort,
            JsonMapper jsonMapper,
            ToolCallMeters toolCallMeters,
            LoggerFactory loggerFactory) {
        this.listCategoriesPort = listCategoriesPort;
        this.jsonMapper = jsonMapper;
        this.toolCallMeters = toolCallMeters;
        this.log = loggerFactory.getLogger(ListCategoriesMcpTool.class);
    }

    @McpTool(
            name = "list_categories",
            description = "Lists the categories filed under one of the caller's groupings. An expense is filed "
                    + "under one of these, never under the grouping itself.")
    public CallToolResult listCategories(
            @McpToolParam(description = "the grouping's name, exactly as it was offered") String grouping) {
        log.debug("Received list_categories call: {}", grouping);

        try {
            AuthenticatedUserId userId = AuthenticatedCaller.authenticatedUserId();

            List<String> categories = listCategoriesPort.list(new ListCategoriesCommand(userId, grouping));
            ListCategoriesToolResponse response = new ListCategoriesToolResponse(grouping, categories);

            log.debug("list_categories call succeeded: {}", response);
            toolCallMeters.countOk("list_categories");
            return CallToolResult.builder()
                    .addTextContent(jsonMapper.writeValueAsString(response))
                    .build();
        } catch (InvalidCategoryException | InvalidGroupingException e) {
            return rejected(e, e.getMessage());
        } catch (InvalidUserException e) {
            return rejected(e, "invalid request: " + e.getMessage());
        } catch (EntityNotFoundException e) {
            return rejected(e, "the user is unknown");
        } catch (PersistenceFailedException e) {
            return rejected(e, "the categories could not be read");
        } catch (RuntimeException e) {
            return rejected(e, "the categories could not be listed", "unexpected");
        }
    }

    private CallToolResult rejected(RuntimeException e, String message) {
        return rejected(e, message, e.getClass().getSimpleName());
    }

    private CallToolResult rejected(RuntimeException e, String message, String reason) {
        log.warn("rejected list_categories call: {} {}", e.getClass().getSimpleName(), e.getMessage());
        toolCallMeters.countRejected("list_categories", reason);
        return CallToolResult.builder().isError(true).addTextContent(message).build();
    }
}
