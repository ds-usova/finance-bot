package bot.finance.adapter.persistence;

import bot.finance.application.dto.KnownCategory;

public record KnownCategoryProjection(String name, String parentName) {

    public KnownCategory toKnownCategory() {
        return new KnownCategory(name, parentName);
    }
}
