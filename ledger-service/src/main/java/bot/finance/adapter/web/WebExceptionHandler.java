package bot.finance.adapter.web;

import bot.finance.adapter.telegram.TelegramLoginRejectedException;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.ExpenseEntryNotFoundException;
import bot.finance.domain.exception.InvalidExpenseAcceptanceException;
import bot.finance.domain.exception.InvalidExpenseCategoryChangeException;
import bot.finance.domain.exception.InvalidExpenseFilterException;
import bot.finance.domain.exception.InvalidMoneyException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.InvalidValueException;
import bot.finance.domain.exception.PersistenceFailedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler(InvalidExpenseAcceptanceException.class)
    public ResponseEntity<Map<String, String>> onInvalidExpenseAcceptance(InvalidExpenseAcceptanceException e) {
        logger.warn("rejected a request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidExpenseCategoryChangeException.class)
    public ResponseEntity<Map<String, String>> onInvalidExpenseCategoryChange(InvalidExpenseCategoryChangeException e) {
        logger.warn("rejected a request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidMoneyException.class)
    public ResponseEntity<Map<String, String>> onInvalidMoney(InvalidMoneyException e) {
        logger.warn("rejected a request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> onMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldErrors().get(0);
        String message = fieldError.getField() + " " + fieldError.getDefaultMessage();

        logger.warn("rejected a request: {}", message);
        return problem(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> onHttpMessageNotReadable(HttpMessageNotReadableException e) {
        logger.warn("rejected a request: the body could not be read");
        return problem(HttpStatus.BAD_REQUEST, "the request body could not be read");
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
     * The fallback for a refused value no handler above names, so a new value object answers 400 from the day it
     * is written. The message is this module's own, never the exception's: only the handlers above it refuse
     * with wording composed for a caller, and the rest name an internal field for a log.
     */
    @ExceptionHandler(InvalidValueException.class)
    public ResponseEntity<Map<String, String>> onInvalidValue(InvalidValueException e) {
        logger.warn("rejected a request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "the request carried a value this service cannot accept");
    }

    @ExceptionHandler(ExpenseEntryNotFoundException.class)
    public ResponseEntity<Map<String, String>> onExpenseEntryNotFound(ExpenseEntryNotFoundException e) {
        logger.warn("rejected a request: {}", e.getMessage());
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
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
