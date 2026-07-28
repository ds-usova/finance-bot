package bot.finance.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Captures what a class logged, so a test can assert on log output instead of on a mocked logger.
 *
 * <p>Attaches a Logback {@link ListAppender} to the logger named after the given class — the name the
 * production {@code Slf4jLoggerFactory} derives — and detaches it again on {@link #close()}. Use it as a
 * try-with-resources block, or attach it in {@code @BeforeEach} and close it in {@code @AfterEach}:
 *
 * <pre>{@code
 * try (LogCapture logCapture = LogCapture.attachedTo(HandleIncomingMessageUseCase.class)) {
 *     ...
 *     assertThat(logCapture.messages()).anyMatch(message -> message.contains("lunch 12 euro"));
 * }
 * }</pre>
 *
 * <p>{@link #messages()} is safe to poll while another thread logs, which asynchronous assertions
 * (Awaitility over a background poll loop) rely on.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final CapturingAppender appender;

    private LogCapture(Logger logger, CapturingAppender appender) {
        this.logger = logger;
        this.appender = appender;
    }

    /**
     * Attaches a fresh appender to the logger named after {@code clazz}.
     */
    public static LogCapture attachedTo(Class<?> clazz) {
        Logger logger = (Logger) LoggerFactory.getLogger(clazz);
        CapturingAppender appender = new CapturingAppender();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        return new LogCapture(logger, appender);
    }

    /**
     * The messages captured so far, with their {@code {}} placeholders already filled in.
     */
    public List<String> messages() {
        return appender.formattedMessages();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }

    /**
     * A {@link ListAppender} whose list is only ever touched under the appender's own lock, so a test thread may
     * read it while a background thread writes to it.
     */
    private static final class CapturingAppender extends ListAppender<ILoggingEvent> {

        @Override
        protected synchronized void append(ILoggingEvent event) {
            super.append(event);
        }

        private synchronized List<String> formattedMessages() {
            return list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        }
    }
}
