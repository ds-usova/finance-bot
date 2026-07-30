package bot.finance.domain.model;

import java.util.Optional;

public abstract class Entity {

    private final Long id;

    protected Entity(Long id) {
        this.id = id;
    }

    public Optional<Long> id() {
        return Optional.ofNullable(id);
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        if (id == null) {
            return false;
        }
        return id.equals(((Entity) other).id);
    }

    @Override
    public final int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }
}
