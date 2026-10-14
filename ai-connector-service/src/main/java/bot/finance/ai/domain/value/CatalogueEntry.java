package bot.finance.ai.domain.value;

import java.util.List;

public record CatalogueEntry(String grouping, List<String> categories) {

    public CatalogueEntry {
        // TODO: refuse a null or blank grouping; a null or empty category list; a null, blank or repeated category name
        categories = List.copyOf(categories);
    }
}
