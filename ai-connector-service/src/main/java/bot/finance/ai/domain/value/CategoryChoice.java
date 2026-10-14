package bot.finance.ai.domain.value;

public record CategoryChoice(String grouping, String category) {

    public CategoryChoice {
        // TODO: refuse a null or blank grouping and a null or blank category
    }
}
