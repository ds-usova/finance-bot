package bot.finance.domain.exception;

public class EntityNotFoundException extends RuntimeException {

    private final String entityType;

    public EntityNotFoundException(String entityType, String message) {
        super(message);
        this.entityType = entityType;
    }

    public String entityType() {
        return entityType;
    }
}
