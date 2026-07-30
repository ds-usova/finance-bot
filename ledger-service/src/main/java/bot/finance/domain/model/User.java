package bot.finance.domain.model;

import bot.finance.domain.exception.InvalidUserException;

public final class User extends Entity {

    private final String externalId;

    private User(Long id, String externalId) {
        super(id);
        if (externalId == null || externalId.isBlank()) {
            throw new InvalidUserException("external id must be present");
        }
        this.externalId = externalId;
    }

    public static User newUser(String externalId) {
        return new User(null, externalId);
    }

    public static User stored(long id, String externalId) {
        return new User(id, externalId);
    }

    public String externalId() {
        return externalId;
    }
}
