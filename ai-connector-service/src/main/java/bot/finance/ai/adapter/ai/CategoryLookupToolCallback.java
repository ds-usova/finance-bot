package bot.finance.ai.adapter.ai;

import bot.finance.ai.application.port.TurnMeters;
import bot.finance.ai.domain.value.CatalogueEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

public class CategoryLookupToolCallback implements ToolCallback {

    private static final String NAME = "list_categories";
    private static final String DESCRIPTION =
            """
            Lists the categories filed under one of the caller's groupings. An expense is filed under one of these, \
            never under the grouping itself.""";
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

    private static final ObjectMapper READER = new ObjectMapper();

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
        try {
            String answer = answer(groupingOf(toolInput));
            turnMeters.countCategoryLookup(true);
            return answer;
        } catch (RuntimeException e) {
            turnMeters.countCategoryLookup(false);
            throw new ToolExecutionException(getToolDefinition(), e);
        }
    }

    private String answer(String grouping) {
        return catalogue.stream()
                .filter(entry -> entry.grouping().equals(grouping))
                .findFirst()
                .map(entry -> write(new Answer(grouping, entry.categories())))
                .orElseThrow(() -> refusal(grouping));
    }

    private RuntimeException refusal(String name) {
        boolean isCategory =
                catalogue.stream().anyMatch(entry -> entry.categories().contains(name));
        if (isCategory) {
            return new RuntimeException(name + " is a category, not a grouping");
        }
        return new RuntimeException("no grouping named " + name + " is stored for this user");
    }

    private static String groupingOf(String toolInput) {
        JsonNode grouping;
        try {
            grouping = READER.readTree(toolInput).get("grouping");
        } catch (JsonProcessingException | RuntimeException e) {
            throw noGrouping();
        }
        if (grouping == null || !grouping.isTextual() || grouping.asText().isBlank()) {
            throw noGrouping();
        }
        return grouping.asText();
    }

    private static RuntimeException noGrouping() {
        return new RuntimeException("list categories command has no grouping name");
    }

    private static String write(Answer answer) {
        try {
            return READER.writeValueAsString(answer);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Answer(String grouping, List<String> categories) {}
}
