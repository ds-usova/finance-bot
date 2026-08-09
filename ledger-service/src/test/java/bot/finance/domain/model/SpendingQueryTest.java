package bot.finance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidSpendingQueryException;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpendingQueryTest {

    private static final SpendingPeriod PERIOD =
            new SpendingPeriod(LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-27"));
    private static final IncomingMessageId MESSAGE_REFERENCE =
            IncomingMessageId.of(java.util.UUID.randomUUID().toString());

    @Nested
    @DisplayName("creating a new spending query")
    class NewQueryFactory {

        @Test
        @DisplayName("when every field is given - then every component reads back unchanged and the entity "
                + "carries no id")
        void whenAllFieldsAreGiven_thenEveryComponentReadsBackUnchangedAndEntityCarriesNoId() {
            Instant now = Instant.parse("2026-07-27T10:15:30Z");

            SpendingQuery query = SpendingQuery.newQuery(1L, PERIOD, MESSAGE_REFERENCE, now);

            assertThat(query.id()).isEmpty();
            assertThat(query.userId()).isEqualTo(1L);
            assertThat(query.period()).isEqualTo(PERIOD);
            assertThat(query.incomingMessageId()).isEqualTo(MESSAGE_REFERENCE);
            assertThat(query.createdAt()).isEqualTo(now);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the user id is zero or negative - then throws InvalidSpendingQueryException")
        void whenUserIdIsZeroOrNegative_thenThrowsInvalidSpendingQueryException(long userId) {
            Instant now = Instant.parse("2026-07-27T10:15:30Z");

            assertThatThrownBy(() -> SpendingQuery.newQuery(userId, PERIOD, MESSAGE_REFERENCE, now))
                    .isInstanceOf(InvalidSpendingQueryException.class);
        }

        @Test
        @DisplayName("when the period is null - then throws InvalidSpendingQueryException")
        void whenPeriodIsNull_thenThrowsInvalidSpendingQueryException() {
            Instant now = Instant.parse("2026-07-27T10:15:30Z");

            assertThatThrownBy(() -> SpendingQuery.newQuery(1L, null, MESSAGE_REFERENCE, now))
                    .isInstanceOf(InvalidSpendingQueryException.class);
        }

        @Test
        @DisplayName("when the message reference is null - then throws InvalidSpendingQueryException")
        void whenMessageReferenceIsNull_thenThrowsInvalidSpendingQueryException() {
            Instant now = Instant.parse("2026-07-27T10:15:30Z");

            assertThatThrownBy(() -> SpendingQuery.newQuery(1L, PERIOD, null, now))
                    .isInstanceOf(InvalidSpendingQueryException.class);
        }

        @Test
        @DisplayName("when the instant is null - then throws InvalidSpendingQueryException")
        void whenInstantIsNull_thenThrowsInvalidSpendingQueryException() {
            assertThatThrownBy(() -> SpendingQuery.newQuery(1L, PERIOD, MESSAGE_REFERENCE, null))
                    .isInstanceOf(InvalidSpendingQueryException.class);
        }
    }

    @Nested
    @DisplayName("reconstituting a stored spending query")
    class StoredFactory {

        @Test
        @DisplayName("when a database id and every other field are given - then the entity carries that id and "
                + "every field unchanged")
        void whenDatabaseIdAndEveryOtherFieldAreGiven_thenEntityCarriesThatIdAndEveryOtherComponentUnchanged() {
            Instant createdAt = Instant.parse("2026-07-27T10:15:30Z");

            SpendingQuery query = SpendingQuery.stored(42L, 1L, PERIOD, MESSAGE_REFERENCE, createdAt);

            assertThat(query.id()).contains(42L);
            assertThat(query.userId()).isEqualTo(1L);
            assertThat(query.period()).isEqualTo(PERIOD);
            assertThat(query.incomingMessageId()).isEqualTo(MESSAGE_REFERENCE);
            assertThat(query.createdAt()).isEqualTo(createdAt);
        }
    }
}
