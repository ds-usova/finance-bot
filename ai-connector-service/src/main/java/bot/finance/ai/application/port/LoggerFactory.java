package bot.finance.ai.application.port;

/**
 * Creates {@link Logger} instances for core classes. A use case takes this factory as a constructor
 * parameter and derives its own logger from it.
 */
public interface LoggerFactory {

    Logger getLogger(Class<?> clazz);
}
