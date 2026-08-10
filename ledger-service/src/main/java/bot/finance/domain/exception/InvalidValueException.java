package bot.finance.domain.exception;

/**
 * The root of every "this value cannot exist" refusal the core raises, so an inbound adapter can answer one of
 * them without having been taught each type: a new value object's exception is refused as a bad request from the
 * day it is written rather than from the day someone remembers to map it.
 *
 * <p>It carries no status and no wording of its own. What a caller is told is the adapter's decision, and the
 * messages below it are not uniformly written for one: some name a field and a bound, others name an internal
 * field for a log.
 */
public class InvalidValueException extends RuntimeException {

    public InvalidValueException(String message) {
        super(message);
    }
}
