package bot.finance.eval.report;

import bot.finance.eval.Attempt;
import java.util.List;

/** The extraction mode a run measured, read from its attempts' usage records. */
final class RunMode {

    private RunMode() {}

    static String of(List<Attempt> attempts) {
        // stub-intent: the one mode the attempts' usage records, "mixed" where two differ, null where none
        // recorded one; an attempt with no usage or no mode is ignored
        return null;
    }
}
