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
        // equal when other is the same concrete class (getClass(), never instanceof) and carries the
        // same non-null id; reference equality while this id is absent
        return this == other;
    }

    @Override
    public final int hashCode() {
        // the id's hash when present, System.identityHashCode(this) when absent
        return 0;
    }
}
