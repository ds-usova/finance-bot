package bot.finance.ai.adapter.ai;

import bot.finance.ai.application.port.TurnMeters;
import bot.finance.ai.domain.value.CatalogueEntry;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class CategoryLookupToolCallback implements ToolCallback {

    private static final String NAME = "list_categories";
    private static final String DESCRIPTION = "Lists the categories filed under one of the caller's groupings. An "
            + "expense is filed under one of these, never under the grouping itself.";
    private static final String INPUT_SCHEMA =
            """
            {
              "type": "object",
              "properties": {
                "grouping": {
                  "type": "string",
                  "description": "the grouping's name, exactly as it was offered"
                }
              },
              "required": ["grouping"]
            }""";

    private final List<CatalogueEntry> catalogue;
    private final TurnMeters turnMeters;

    public CategoryLookupToolCallback(List<CatalogueEntry> catalogue, TurnMeters turnMeters) {
        this.catalogue = catalogue;
        this.turnMeters = turnMeters;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .inputSchema(INPUT_SCHEMA)
                .build();
    }

    @Override
    public String call(String toolInput) {
        // stub-intent: reads the grouping out of the call's arguments, answers that grouping's categories from the
        // turn's catalogue in the ledger's answer form, or refuses with the ledger's refusal text; counts the lookup
        return "";
    }
}
