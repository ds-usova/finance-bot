package bot.finance.application.dto;

import java.util.List;

public record CatalogueEntry(String grouping, List<String> categories) {

    public static List<String> groupingsOf(List<CatalogueEntry> catalogue) {
        return catalogue.stream()
                .map(entry -> entry == null ? null : entry.grouping())
                .toList();
    }
}
