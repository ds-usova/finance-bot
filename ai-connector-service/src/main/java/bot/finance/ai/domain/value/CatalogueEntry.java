package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record CatalogueEntry(String grouping, List<String> categories) {

    public CatalogueEntry {
        if (grouping == null || grouping.isBlank()) {
            throw new InvalidValueException("Grouping must not be null or blank");
        }
        if (categories == null || categories.isEmpty()) {
            throw new InvalidValueException("Categories must not be null or empty");
        }
        Set<String> seen = new HashSet<>();
        for (String category : categories) {
            if (category == null || category.isBlank()) {
                throw new InvalidValueException("Category name must not be null or blank");
            }
            if (!seen.add(category)) {
                throw new InvalidValueException("Category name must not be repeated: " + category);
            }
        }
        categories = List.copyOf(categories);
    }

    public List<CategoryChoice> choices() {
        return categories.stream()
                .map(category -> new CategoryChoice(grouping, category))
                .toList();
    }
}
