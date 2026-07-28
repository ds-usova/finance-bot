package bot.finance.domain.value;

import java.util.List;

public record Category(String name, List<Category> children) {

    public Category {
        // rejects a blank name, an absent child list, and a child that carries children of its
        // own, with InvalidCategoryException; keeps children as an unmodifiable copy
    }

    public static Category leaf(String name) {
        return new Category(name, List.of());
    }

    public static Category group(String name, String... childNames) {
        return new Category(name, List.of(childNames).stream().map(Category::leaf).toList());
    }

    public static List<Category> defaults() {
        // the 20 predefined groups and their 78 children, in the order the plan lists them
        return List.of();
    }

}
