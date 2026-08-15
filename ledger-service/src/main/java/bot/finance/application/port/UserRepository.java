package bot.finance.application.port;

import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Grouping;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    /**
     * @throws PersistenceFailedException if the lookup fails
     */
    Optional<User> findByExternalId(String externalId);

    /**
     * @throws PersistenceFailedException if the lookup fails
     */
    Optional<User> findById(long userId);

    /**
     * The caller a use case is acting for, refused where the store holds none. Every use case that acts on
     * somebody's own ledger needs the stored id before it can read or write anything, and an absent row is the
     * same refusal each time — a session outliving its user, or a tool called for somebody who never signed in.
     * A use case for which absence is not a refusal calls {@link #findByExternalId} and decides for itself.
     *
     * @throws EntityNotFoundException if no user is stored under that external id
     * @throws PersistenceFailedException if the lookup fails
     */
    default User requireByExternalId(String externalId) {
        return findByExternalId(externalId)
                .orElseThrow(
                        () -> new EntityNotFoundException("user", "no user stored under external id " + externalId));
    }

    /**
     * The caller a use case is acting for, refused where the store holds none, resolved by internal id rather
     * than external id.
     *
     * @throws EntityNotFoundException if no user is stored under that id
     * @throws PersistenceFailedException if the lookup fails
     */
    default User requireById(long userId) {
        return findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("user", "no user stored under id " + userId));
    }

    /**
     * @throws InvalidUserException if the user's external id violates a column constraint
     * @throws InvalidGroupingException if a grouping name violates a column constraint
     * @throws InvalidCategoryException if a category name violates a column constraint
     * @throws PersistenceFailedException if the write fails
     */
    User create(User user, List<Grouping> groupings);
}
