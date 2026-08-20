package bot.finance.ai.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.ai.common.boot.PersistenceAdapterTest;
import bot.finance.ai.common.rows.StreamEntryFailureRowUtils;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.exception.MessageStoreUnavailableException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@PersistenceAdapterTest
@Import(JdbcChangeAttemptStoreAdapter.class)
class JdbcChangeAttemptStoreAdapterTest {

    @Autowired
    private JdbcChangeAttemptStoreAdapter adapter;

    @Autowired
    private StreamEntryFailureEntityRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("counting a failed attempt")
    class CountFailure {

        @Test
        @DisplayName(
                "when no row exists for the entry id - then answers 1, the row holds the error and a first-failed-at")
        void whenNoRowExistsForEntryId_thenAnswers1AndRowHoldsErrorAndFirstFailedAt() {
            String entryId = "count-failure-new-entry";

            int attempts = adapter.countFailure(entryId, "connection refused");

            assertThat(attempts).isEqualTo(1);
            StreamEntryFailureEntity row = repository.findById(entryId).orElseThrow();
            assertThat(row.lastError()).isEqualTo("connection refused");
            assertThat(row.firstFailedAt()).isNotNull();
        }

        @Test
        @DisplayName("when called twice more with another error - then answers 2 then 3, keeping the first-failed-at")
        void whenCalledTwiceMoreWithAnotherError_thenAnswers2Then3KeepingFirstFailedAt() {
            String entryId = "count-failure-repeated-entry";
            adapter.countFailure(entryId, "first error");
            Instant firstFailedAt = repository.findById(entryId).orElseThrow().firstFailedAt();

            int second = adapter.countFailure(entryId, "second error");
            int third = adapter.countFailure(entryId, "second error");

            assertThat(second).isEqualTo(2);
            assertThat(third).isEqualTo(3);
            StreamEntryFailureEntity row = repository.findById(entryId).orElseThrow();
            assertThat(row.lastError()).isEqualTo("second error");
            assertThat(row.firstFailedAt()).isEqualTo(firstFailedAt);
        }

        @Test
        @DisplayName("when called for one of two entry ids' rows - then only that entry's attempts change")
        void whenCalledForOneOfTwoEntryIdsRows_thenOnlyThatEntryAttemptsChange() {
            String firstEntryId = "count-failure-first-entry";
            String secondEntryId = "count-failure-second-entry";
            adapter.countFailure(firstEntryId, "error");
            adapter.countFailure(secondEntryId, "error");

            adapter.countFailure(firstEntryId, "error again");

            assertThat(StreamEntryFailureRowUtils.attempts(jdbcTemplate, firstEntryId))
                    .isEqualTo(2);
            assertThat(StreamEntryFailureRowUtils.attempts(jdbcTemplate, secondEntryId))
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("clearing an entry's attempts")
    class Clear {

        @Test
        @DisplayName("when a row exists for the entry id - then it is gone, and a second clear changes nothing")
        void whenRowExistsForEntryId_thenGoneAndSecondClearChangesNothing() {
            String entryId = "clear-existing-entry";
            StreamEntryFailureRowUtils.insert(jdbcTemplate, entryId, 2, Instant.now(), "some error");

            adapter.clear(entryId);

            assertThat(repository.findById(entryId)).isEmpty();

            adapter.clear(entryId);

            assertThat(repository.findById(entryId)).isEmpty();
        }
    }

    // The scenarios below need a store that fails in a way the healthy containerized Postgres cannot be
    // made to. Each constructs its own adapter over a Mockito mock and calls the adapter's own public
    // methods directly - it is still the adapter under test, just not wired against the real database.
    @Nested
    @DisplayName("against a mocked repository, not the containerized database")
    class WithAMockedRepository {

        private final StreamEntryFailureEntityRepository mockedRepository =
                mock(StreamEntryFailureEntityRepository.class);
        private final JdbcChangeAttemptStoreAdapter mockedAdapter = new JdbcChangeAttemptStoreAdapter(mockedRepository);

        @Test
        @DisplayName("when the repository throws a resource-failure exception - then throws "
                + "MessageStoreUnavailableException")
        void whenRepositoryThrowsResourceFailureException_thenThrowsMessageStoreUnavailableException() {
            DataAccessResourceFailureException frameworkException =
                    new DataAccessResourceFailureException("connection refused");
            when(mockedRepository.countFailure(anyString(), anyString(), any(Instant.class)))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.countFailure("mocked-entry", "error"))
                    .isInstanceOf(MessageStoreUnavailableException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when the repository throws another DataAccessException - then throws MessageStoreFailedException, "
                        + "not its subtype")
        void whenRepositoryThrowsAnotherDataAccessException_thenThrowsMessageStoreFailedExceptionNotSubtype() {
            DataIntegrityViolationException frameworkException =
                    new DataIntegrityViolationException("constraint violated");
            when(mockedRepository.countFailure(anyString(), anyString(), any(Instant.class)))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.countFailure("mocked-entry-2", "error"))
                    .isInstanceOf(MessageStoreFailedException.class)
                    .isNotInstanceOf(MessageStoreUnavailableException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }
    }
}
