package bot.finance.adapter.persistence;

import bot.finance.adapter.cdc.CategoryNames;

public record CategoryNamesProjection(String name, String groupingName) {

    public CategoryNames toCategoryNames() {
        return new CategoryNames(name, groupingName);
    }
}
