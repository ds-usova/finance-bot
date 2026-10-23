package bot.finance.eval.corpus;

/** One action of a steps file; {@code message} and {@code learningId} are null where the action takes none. */
public record Step(String action, String message, String learningId) {}
