package bot.finance.adapter.web;

import bot.finance.adapter.telegram.TelegramLoginRejectedException;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = SessionController.class)
public class WebExceptionHandler {

    private final Logger logger;

    public WebExceptionHandler(LoggerFactory loggerFactory) {
        this.logger = loggerFactory.getLogger(WebExceptionHandler.class);
    }

    /** The payload and its hash are never logged or echoed: a rejected one is still a signed credential. */
    @ExceptionHandler(TelegramLoginRejectedException.class)
    public ResponseEntity<Map<String, String>> onLoginRejected(TelegramLoginRejectedException e) {
        logger.warn("rejected a Telegram sign-in: {}", e.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "the Telegram sign-in was not accepted");
    }

    @ExceptionHandler(InvalidUserException.class)
    public ResponseEntity<Map<String, String>> onInvalidUser(InvalidUserException e) {
        logger.warn("rejected a session request: {}", e.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "no browser session is open");
    }

    @ExceptionHandler(PersistenceFailedException.class)
    public ResponseEntity<Map<String, String>> onPersistenceFailed(PersistenceFailedException e) {
        logger.error("a session request could not be stored", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "the service is temporarily unable to store the session");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> onUnexpected(Exception e) {
        logger.error("a session request failed unexpectedly", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "the request could not be completed");
    }

    private static ResponseEntity<Map<String, String>> problem(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
