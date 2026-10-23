package bot.finance.eval.oracle;

/** One comparison of a declared expectation with what the application did; status is pass, fail or not-evaluated. */
public record Check(String path, Object expected, Object actual, String status) {

    static Check of(String path, Object expected, Object actual, boolean passed) {
        return new Check(path, expected, actual, passed ? "pass" : "fail");
    }

    static Check notEvaluated(String path, Object expected) {
        return new Check(path, expected, null, "not-evaluated");
    }
}
