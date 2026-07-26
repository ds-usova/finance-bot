package bot.finance.ai.application.dto;

public record RawIntent(
        String target,
        String operation,
        String categoryName,
        String newCategoryName,
        String amount,
        String currency,
        String description) {

}
