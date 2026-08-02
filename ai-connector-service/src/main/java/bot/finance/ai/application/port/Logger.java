package bot.finance.ai.application.port;

/**
 * Logging abstraction owned by the application core, so {@code domain}/{@code application} depend on no
 * external logging API.
 *
 * <p>Messages may contain {@code {}} placeholders, filled from {@code args} in order
 * (e.g. {@code log.info("expense {} recorded for user {}", expenseId, userId)}); a {@link Throwable} passed
 * as the last argument is logged with its stack trace. Formatting is deferred until the level is enabled.
 */
public interface Logger {

    void debug(String message, Object... args);

    void info(String message, Object... args);

    void warn(String message, Object... args);

    void error(String message, Object... args);

    void error(String message, Throwable t);
}
