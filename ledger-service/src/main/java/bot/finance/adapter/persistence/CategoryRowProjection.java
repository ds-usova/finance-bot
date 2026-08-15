package bot.finance.adapter.persistence;

import bot.finance.adapter.cdc.CategoryRow;
import java.util.Optional;

public record CategoryRowProjection(String name, Long parentId) {

    public CategoryRow toCategoryRow() {
        return new CategoryRow(name, Optional.ofNullable(parentId));
    }
}
