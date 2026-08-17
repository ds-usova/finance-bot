package bot.finance.ai.adapter.ai;

import bot.finance.ai.domain.value.ExampleExpense;
import bot.finance.ai.domain.value.MessageExample;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ExampleSectionRenderer {

    public String render(Optional<List<MessageExample>> examples) {
        if (examples.isEmpty()) {
            return "";
        }

        List<MessageExample> found = examples.get();
        if (found.isEmpty()) {
            return "none";
        }

        return found.stream().map(this::renderExample).collect(Collectors.joining("\n"));
    }

    private String renderExample(MessageExample example) {
        String expenseLines = example.expenses().stream()
                .map(this::renderExpenseLine)
                .collect(Collectors.joining("\n"));

        return "- \"" + example.text() + "\"\n" + expenseLines;
    }

    private String renderExpenseLine(ExampleExpense expense) {
        StringBuilder line = new StringBuilder("  - ")
                .append(expense.description())
                .append(", ")
                .append(expense.amount())
                .append(" ")
                .append(expense.currency().code());

        String categoryAndGrouping = renderCategoryAndGrouping(expense);
        if (!categoryAndGrouping.isEmpty()) {
            line.append(" — ").append(categoryAndGrouping);
        }

        line.append(" — ").append(expense.outcome().name().toLowerCase());

        return line.toString();
    }

    private String renderCategoryAndGrouping(ExampleExpense expense) {
        Optional<String> category = expense.categoryName();
        Optional<String> grouping = expense.groupingName();

        if (category.isPresent() && grouping.isPresent()) {
            return category.get() + " (" + grouping.get() + ")";
        }
        if (category.isPresent()) {
            return category.get();
        }
        return grouping.orElse("");
    }
}
