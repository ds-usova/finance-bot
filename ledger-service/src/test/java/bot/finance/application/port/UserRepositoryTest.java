package bot.finance.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Grouping;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The port's own default method, which every use case acting on somebody's ledger now leans on. A hand-written
 * implementation rather than a mock: a mock intercepts a default method too, so mocking the port would prove
 * nothing about the body under test.
 */
class UserRepositoryTest {

    private static final String EXTERNAL_ID = "user-external-id-42";

    @Nested
    @DisplayName("requiring the caller a use case is acting for")
    class RequireByExternalId {

        @Test
        @DisplayName("when a user is stored under that external id - then it is answered")
        void whenAUserIsStoredUnderThatExternalId_thenItIsAnswered() {
            User stored = User.stored(7L, EXTERNAL_ID);

            assertThat(repositoryAnswering(Optional.of(stored)).requireByExternalId(EXTERNAL_ID))
                    .isEqualTo(stored);
        }

        @Test
        @DisplayName("when no user is stored under that external id - then EntityNotFoundException names the id")
        void whenNoUserIsStoredUnderThatExternalId_thenEntityNotFoundExceptionNamesTheId() {
            assertThatThrownBy(() -> repositoryAnswering(Optional.empty()).requireByExternalId(EXTERNAL_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(EXTERNAL_ID);
        }

        @Test
        @DisplayName("when the lookup itself fails - then that failure propagates rather than becoming a refusal")
        void whenTheLookupItselfFails_thenThatFailurePropagatesRatherThanBecomingARefusal() {
            PersistenceFailedException failure = new PersistenceFailedException("store down", new RuntimeException());

            assertThatThrownBy(() -> repositoryThrowing(failure).requireByExternalId(EXTERNAL_ID))
                    .isSameAs(failure);
        }
    }

    private static UserRepository repositoryAnswering(Optional<User> answer) {
        return new StubUserRepository() {
            @Override
            public Optional<User> findByExternalId(String externalId) {
                return answer;
            }
        };
    }

    private static UserRepository repositoryThrowing(RuntimeException failure) {
        return new StubUserRepository() {
            @Override
            public Optional<User> findByExternalId(String externalId) {
                throw failure;
            }
        };
    }

    private abstract static class StubUserRepository implements UserRepository {

        @Override
        public User create(User user, List<Grouping> groupings) {
            throw new UnsupportedOperationException("not part of what this test exercises");
        }
    }
}
