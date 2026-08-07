package bot.finance.adapter.web;

import bot.finance.adapter.telegram.TelegramLoginRejectedException;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseFilterException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(InvalidExpenseFilterException.class)
    public ResponseEntity<Map<String, String>> onInvalidExpenseFilter(InvalidExpenseFilterException e) {
        logger.warn("rejected a request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidSpendingPeriodException.class)
    public ResponseEntity<Map<String, String>> onInvalidSpendingPeriod(InvalidSpendingPeriodException e) {
        logger.warn("rejected a request: {}", e.getMessage());
        return problem(
                HttpStatus.BAD_REQUEST,
                "from and to must both be given, as a YYYY-MM-DD day, with from no later than to");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> onTypeMismatch(MethodArgumentTypeMismatchException e) {
        logger.warn("rejected a request: {} could not be read", e.getName());
        return problem(HttpStatus.BAD_REQUEST, e.getName() + " is not a valid value: '" + e.getValue() + "'");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> onConstraintViolation(ConstraintViolationException e) {
        ConstraintViolation<?> violation =
                e.getConstraintViolations().iterator().next();
        String name = lastPathSegment(violation.getPropertyPath().toString());
        String bound = violation.getMessage();

        logger.warn("rejected a request: {} {}", name, bound);
        return problem(HttpStatus.BAD_REQUEST, name + " " + bound);
    }

    /**
     * The generated endpoint interfaces bind {@code status} as a plain {@code String}, so an unrecognized value
     * surfaces as {@code ExpenseStatus.valueOf} refusing it rather than as a binding failure.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onIllegalArgument(IllegalArgumentException e) {
        logger.warn("rejected a request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "status must be PENDING or RECORDED");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> onEntityNotFound(EntityNotFoundException e) {
        logger.warn("rejected a request: {}", e.getMessage());
        return problem(HttpStatus.NOT_FOUND, "the caller is unknown");
    }

    @ExceptionHandler(PersistenceFailedException.class)
    public ResponseEntity<Map<String, String>> onPersistenceFailed(PersistenceFailedException e) {
        logger.error("a request could not be completed because the store was unavailable", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "the service is temporarily unable to complete the request");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> onUnexpected(Exception e) {
        logger.error("a request failed unexpectedly", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "the request could not be completed");
    }

    private static String lastPathSegment(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }

    private static ResponseEntity<Map<String, String>> problem(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
