package bot.finance.eval;

/** A failure that stops an attempt, with the stage it stopped in and the HTTP status where one was answered. */
public class EvalException extends RuntimeException {

    private final String stage;
    private final Integer status;

    public EvalException(String stage, String message) {
        this(stage, message, null, null);
    }

    public EvalException(String stage, String message, Integer status) {
        this(stage, message, status, null);
    }

    public EvalException(String stage, String message, Integer status, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.status = status;
    }

    public String stage() {
        return stage;
    }

    public Integer status() {
        return status;
    }
}
