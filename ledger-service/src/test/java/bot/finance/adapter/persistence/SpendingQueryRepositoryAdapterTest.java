package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.SpendingQueryRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.SpendingQuery;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@PersistenceAdapterTest
@Import(SpendingQueryRepositoryAdapter.class)
class SpendingQueryRepositoryAdapterTest {

    @Autowired
    private SpendingQueryRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("creating a spending query")
    class Create {

        @Test
        @DisplayName(
                "when called with a stored user and a query over a one-week period - then the row holds what was given and the returned entity carries a generated id")
        void whenCalledWithStoredUser_thenRowWrittenWithGivenFieldsAndReturnedEntityCarriesGeneratedId() {
            long userId = storedUserId("spending-query-create-user");
            MessageReference reference = MessageReference.newReference();
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));
            Instant now = Instant.now();
            SpendingQuery query = SpendingQuery.newQuery(userId, period, reference, now);

            SpendingQuery created = adapter.create(query);

            assertThat(created.id()).isPresent();
            List<SpendingQueryEntity> rows = spendingQueryRowsFor(userId);
            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.userId()).isEqualTo(userId);
                assertThat(row.messageReference()).isEqualTo(reference.value());
                assertThat(row.periodStart()).isEqualTo(period.from());
                assertThat(row.periodEnd()).isEqualTo(period.to());
                assertThat(row.createdAt()).isEqualTo(now.truncatedTo(ChronoUnit.MICROS));
            });
        }

        @Test
        @DisplayName(
                "when called with a query whose user id names no stored user - then throws EntityNotFoundException naming the user")
        void whenUserIdNamesNoStoredUser_thenThrowsEntityNotFoundExceptionForUser() {
            long unknownUserId = 999_999_999L;
            SpendingQuery query = SpendingQuery.newQuery(
                    unknownUserId,
                    new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26)),
                    MessageReference.newReference(),
                    Instant.now());

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> adapter.create(query))
                    .extracting(EntityNotFoundException::entityType)
                    .isEqualTo("user");
        }
    }

    @Nested
    @DisplayName("finding periods by message reference")
    class FindPeriodsByMessageReference {

        @Test
        @DisplayName(
                "when two rows under one reference carry the same period, written at different instants - then that period is answered once")
        void whenTwoRowsCarrySamePeriod_thenPeriodAnsweredOnce() {
            long userId = storedUserId("spending-query-duplicate-period-user");
            MessageReference reference = MessageReference.newReference();
            LocalDate from = LocalDate.of(2026, 7, 20);
            LocalDate to = LocalDate.of(2026, 7, 26);
            Instant base = Instant.now().minusSeconds(60);
            storedQuery(userId, reference.value(), from, to, base);
            storedQuery(userId, reference.value(), from, to, base.plusSeconds(10));

            List<SpendingPeriod> periods = adapter.findPeriodsByMessageReference(userId, reference);

            assertThat(periods).containsExactly(new SpendingPeriod(from, to));
        }

        @Test
        @DisplayName(
                "when three rows under one reference carry three different periods, written out of order - then all three are answered, oldest first by the instant they were written")
        void whenThreeRowsCarryThreeDifferentPeriods_thenAllThreeAnsweredOldestFirst() {
            long userId = storedUserId("spending-query-three-periods-user");
            MessageReference reference = MessageReference.newReference();
            SpendingPeriod first = new SpendingPeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7));
            SpendingPeriod second = new SpendingPeriod(LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14));
            SpendingPeriod third = new SpendingPeriod(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 21));
            Instant base = Instant.now().minusSeconds(60);
            storedQuery(userId, reference.value(), third.from(), third.to(), base.plusSeconds(20));
            storedQuery(userId, reference.value(), first.from(), first.to(), base);
            storedQuery(userId, reference.value(), second.from(), second.to(), base.plusSeconds(10));

            List<SpendingPeriod> periods = adapter.findPeriodsByMessageReference(userId, reference);

            assertThat(periods).containsExactly(first, second, third);
        }

        @Test
        @DisplayName(
                "when rows under two different references exist for the same user - then only the requested reference's periods are answered")
        void whenTwoReferencesExistForSameUser_thenOnlyRequestedReferencesPeriodsAnswered() {
            long userId = storedUserId("spending-query-two-references-user");
            MessageReference reference = MessageReference.newReference();
            MessageReference otherReference = MessageReference.newReference();
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));
            SpendingPeriod otherPeriod = new SpendingPeriod(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 7));
            Instant now = Instant.now().minusSeconds(30);
            storedQuery(userId, reference.value(), period.from(), period.to(), now);
            storedQuery(userId, otherReference.value(), otherPeriod.from(), otherPeriod.to(), now);

            List<SpendingPeriod> periods = adapter.findPeriodsByMessageReference(userId, reference);

            assertThat(periods).containsExactly(period);
        }

        @Test
        @DisplayName(
                "when two users each have a row under the same reference value - then only the requested user's period is answered")
        void whenTwoUsersShareReferenceValue_thenOnlyRequestedUsersPeriodAnswered() {
            long firstUserId = storedUserId("spending-query-shared-reference-first-user");
            long secondUserId = storedUserId("spending-query-shared-reference-second-user");
            MessageReference sharedReference = MessageReference.newReference();
            SpendingPeriod firstPeriod = new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));
            SpendingPeriod secondPeriod = new SpendingPeriod(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 7));
            Instant now = Instant.now().minusSeconds(30);
            storedQuery(firstUserId, sharedReference.value(), firstPeriod.from(), firstPeriod.to(), now);
            storedQuery(secondUserId, sharedReference.value(), secondPeriod.from(), secondPeriod.to(), now);

            List<SpendingPeriod> periods = adapter.findPeriodsByMessageReference(firstUserId, sharedReference);

            assertThat(periods).containsExactly(firstPeriod);
        }

        @Test
        @DisplayName("when called with a reference no row carries - then returns an empty list")
        void whenReferenceHasNoStoredRows_thenReturnsEmptyList() {
            long userId = storedUserId("spending-query-no-rows-user");

            List<SpendingPeriod> periods =
                    adapter.findPeriodsByMessageReference(userId, MessageReference.newReference());

            assertThat(periods).isEmpty();
        }
    }

    // The scenarios below need a store that misbehaves in a way the healthy containerized
    // Postgres cannot be made to: a non-constraint failure. They construct their own adapter over
    // a Mockito mock and call the adapter's own public method directly - it is still the adapter
    // under test, just not wired against the real database.
    @Nested
    @DisplayName("discarding the periods asked about under one message")
    class Discard {

        @Test
        @DisplayName("when two rows are stored under the reference - then both are removed and two is answered")
        void whenTwoRowsStoredUnderReference_thenBothRemovedAndCountAnswered() {
            long userId = storedUserId("spending-query-discard-user");
            MessageReference reference = MessageReference.newReference();
            Instant now = Instant.now();
            storedQuery(userId, reference.value(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), now);
            storedQuery(userId, reference.value(), LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14), now);

            int discarded = adapter.discard(userId, reference);

            assertThat(discarded).isEqualTo(2);
            assertThat(adapter.findPeriodsByMessageReference(userId, reference)).isEmpty();
        }

        @Test
        @DisplayName("when another message's rows are stored for the same user - then only the named message's rows go")
        void whenAnotherMessagesRowsExist_thenOnlyTheNamedMessagesRowsGo() {
            long userId = storedUserId("spending-query-discard-other-reference-user");
            MessageReference discarded = MessageReference.newReference();
            MessageReference kept = MessageReference.newReference();
            SpendingPeriod keptPeriod = new SpendingPeriod(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 7));
            Instant now = Instant.now();
            storedQuery(userId, discarded.value(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), now);
            storedQuery(userId, kept.value(), keptPeriod.from(), keptPeriod.to(), now);

            adapter.discard(userId, discarded);

            assertThat(adapter.findPeriodsByMessageReference(userId, discarded)).isEmpty();
            assertThat(adapter.findPeriodsByMessageReference(userId, kept)).containsExactly(keptPeriod);
        }

        @Test
        @DisplayName("when two users hold rows under the same reference value - then only the named user's rows go")
        void whenTwoUsersShareReferenceValue_thenOnlyTheNamedUsersRowsGo() {
            long userId = storedUserId("spending-query-discard-first-user");
            long otherUserId = storedUserId("spending-query-discard-second-user");
            MessageReference reference = MessageReference.newReference();
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 7));
            Instant now = Instant.now();
            storedQuery(userId, reference.value(), period.from(), period.to(), now);
            storedQuery(otherUserId, reference.value(), period.from(), period.to(), now);

            adapter.discard(userId, reference);

            assertThat(adapter.findPeriodsByMessageReference(userId, reference)).isEmpty();
            assertThat(adapter.findPeriodsByMessageReference(otherUserId, reference))
                    .containsExactly(period);
        }

        @Test
        @DisplayName("when no row is stored under the reference - then nothing is removed and zero is answered")
        void whenNoRowStoredUnderReference_thenNothingRemovedAndZeroAnswered() {
            long userId = storedUserId("spending-query-discard-nothing-user");

            int discarded = adapter.discard(userId, MessageReference.newReference());

            assertThat(discarded).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("against a mocked store, not the containerized database")
    class WithAMockedStore {

        private final SpendingQueryEntityRepository mockedSpendingQueryEntityRepository =
                mock(SpendingQueryEntityRepository.class);
        private final SpendingQueryRepositoryAdapter mockedAdapter =
                new SpendingQueryRepositoryAdapter(mockedSpendingQueryEntityRepository);

        @Test
        @DisplayName(
                "when create() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void whenCreateHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedSpendingQueryEntityRepository.save(any())).thenThrow(frameworkException);
            SpendingQuery query = SpendingQuery.newQuery(
                    1L,
                    new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26)),
                    MessageReference.newReference(),
                    Instant.now());

            assertThatThrownBy(() -> mockedAdapter.create(query))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when findPeriodsByMessageReference() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenFindPeriodsByMessageReferenceHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedSpendingQueryEntityRepository.findPeriodsByMessageReference(any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.findPeriodsByMessageReference(1L, MessageReference.newReference()))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when discard() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void whenDiscardHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedSpendingQueryEntityRepository.discard(any(), any())).thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.discard(1L, MessageReference.newReference()))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private List<SpendingQueryEntity> spendingQueryRowsFor(long userId) {
        return SpendingQueryRowUtils.spendingQueryRowsFor(jdbcAggregateTemplate, userId);
    }

    private SpendingQueryEntity storedQuery(
            long userId, UUID messageReference, LocalDate periodStart, LocalDate periodEnd, Instant createdAt) {
        return SpendingQueryRowUtils.storedQuery(
                jdbcAggregateTemplate, userId, messageReference, periodStart, periodEnd, createdAt);
    }
}
