package bot.finance.eval.corpus;

/** A message filed before the target, with the classification and decision the person applies to its proposals. */
public record LearningEntry(
        String id, String message, int proposalCount, String grouping, String category, String decision) {

    public boolean approves() {
        return "approve".equals(decision);
    }
}
