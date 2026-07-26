package bot.finance.ai.adapter.ai;

public record ExtractedIntent(
        String target,
        String operation,
        String categoryName,
        String newCategoryName,
        String amount,
        String currency,
        String description) {

}
