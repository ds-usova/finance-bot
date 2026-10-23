package bot.finance.eval.ledger;

/** A pending entry as REST answers it, with its category and grouping resolved to catalogue names. */
public record Proposal(
        long id,
        String amount,
        String currency,
        String merchant,
        String description,
        long categoryId,
        String category,
        String grouping) {

    /** The value a constraint path names, or null where the path names nothing a proposal has. */
    public String field(String path) {
        return switch (path) {
            case "money.amount" -> amount;
            case "money.currency" -> currency;
            case "merchant" -> merchant;
            case "description" -> description;
            case "category" -> category;
            case "grouping" -> grouping;
            default -> throw new IllegalArgumentException("no proposal field " + path);
        };
    }
}
