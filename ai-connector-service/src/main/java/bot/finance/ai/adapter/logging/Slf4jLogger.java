package bot.finance.ai.adapter.logging;

import bot.finance.ai.application.port.Logger;

public class Slf4jLogger implements Logger {

    private final org.slf4j.Logger delegate;

    public Slf4jLogger(org.slf4j.Logger delegate) {
        this.delegate = delegate;
    }

    @Override
    public void debug(String message, Object... args) {
        delegate.debug(message, args);
    }

    @Override
    public void info(String message, Object... args) {
        delegate.info(message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        delegate.warn(message, args);
    }

    @Override
    public void error(String message, Object... args) {
        delegate.error(message, args);
    }

    @Override
    public void error(String message, Throwable t) {
        delegate.error(message, t);
    }
}
