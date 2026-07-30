package bot.finance.domain.model;

public final class User extends Entity {

    private final String externalId;

    private User(Long id, String externalId) {
        super(id);
        // asserts a present, non-blank external id, throwing InvalidUserException
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
