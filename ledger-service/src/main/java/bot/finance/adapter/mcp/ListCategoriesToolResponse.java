package bot.finance.adapter.mcp;

import java.util.List;

public record ListCategoriesToolResponse(String parentCategory, List<String> categories) {}
