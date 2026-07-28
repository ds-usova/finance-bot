package bot.finance.domain.model;

import java.util.Objects;
import java.util.Optional;

public final class User {

    private final Long id;
    private final String externalId;

    private User(Long id, String externalId) {
        this.id = id;
        this.externalId = externalId;
    }

    public static User newUser(String externalId) {
        return new User(null, externalId);
    }

    public static User stored(long id, String externalId) {
        return new User(id, externalId);
    }

    public Optional<Long> id() {
        return Optional.ofNullable(id);
    }

    public String externalId() {
        return externalId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user)) {
            return false;
        }
        return Objects.equals(externalId, user.externalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalId);
    }
}
