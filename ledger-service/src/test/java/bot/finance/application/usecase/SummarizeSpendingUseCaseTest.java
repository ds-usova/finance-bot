package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.SummarizeSpendingCommand;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.exception.InvalidSpendingQueryException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.SpendingQuery;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SummarizeSpendingUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-05T00:00:00Z");
    private static final SpendingPeriod EXPECTED_PERIOD =
            new SpendingPeriod(LocalDate.parse("2026-07-27"), LocalDate.parse("2026-08-02"));

    private UserRepository userRepository;
    private SpendingQueryRepository spendingQueryRepository;
    private Clock clock;
    private SummarizeSpendingUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        spendingQueryRepository = mock(SpendingQueryRepository.class);
        clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        useCase = new SummarizeSpendingUseCase(userRepository, spendingQueryRepository, clock);
    }

    private SummarizeSpendingCommand newCommand(IncomingMessageId reference, String from, String to) {
        return new SummarizeSpendingCommand(new AuthenticatedUserId(EXTERNAL_ID), reference, from, to);
    }

    /** Stores a user under {@code userId}, and answers the query the use case writes for EXPECTED_PERIOD. */
    private void stubStoredUserAndCreatedQuery(long userId, IncomingMessageId reference) {
        when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(User.stored(userId, EXTERNAL_ID)));
        when(spendingQueryRepository.create(any()))
                .thenReturn(SpendingQuery.stored(9L, userId, EXPECTED_PERIOD, reference, FIXED_INSTANT));
    }

    private SpendingQuery capturedQuery() {
        ArgumentCaptor<SpendingQuery> captor = ArgumentCaptor.forClass(SpendingQuery.class);
        verify(spendingQueryRepository).create(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("summarizing a period")
    class Summarize {

        @Test
        @DisplayName("when the command is absent - then throws InvalidSpendingQueryException and neither "
                + "repository is touched")
        void whenCommandIsAbsent_thenThrowsInvalidSpendingQueryExceptionAndRepositoriesUntouched() {
            assertThatThrownBy(() -> useCase.summarize(null)).isInstanceOf(InvalidSpendingQueryException.class);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(spendingQueryRepository);
        }

        @Test
        @DisplayName("when the written dates do not make a period - then throws InvalidSpendingPeriodException")
        void whenWrittenDatesDoNotMakeAPeriod_thenThrowsInvalidSpendingPeriodException() {
            SummarizeSpendingCommand command = newCommand(
                    IncomingMessageId.of(java.util.UUID.randomUUID().toString()), "not-a-date", "2026-08-05");

            assertThatThrownBy(() -> useCase.summarize(command)).isInstanceOf(InvalidSpendingPeriodException.class);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(spendingQueryRepository);
        }

        @Test
        @DisplayName("when nothing is stored under the command's external id - then throws EntityNotFoundException")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundException() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());
            SummarizeSpendingCommand command = newCommand(
                    IncomingMessageId.of(java.util.UUID.randomUUID().toString()), "2026-08-01", "2026-08-05");

            assertThatThrownBy(() -> useCase.summarize(command)).isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(spendingQueryRepository);
        }

        @Test
        @DisplayName("when a well-formed period is given - then the stored query carries the user, reference, "
                + "period and instant")
        void whenWellFormedPeriod_thenStoredQueryCarriesUserReferencePeriodAndClockInstant() {
            IncomingMessageId reference =
                    IncomingMessageId.of(java.util.UUID.randomUUID().toString());
            stubStoredUserAndCreatedQuery(USER_ID, reference);

            useCase.summarize(newCommand(reference, "2026-07-27", "2026-08-02"));

            SpendingQuery stored = capturedQuery();
            assertThat(stored.userId()).isEqualTo(USER_ID);
            assertThat(stored.incomingMessageId()).isEqualTo(reference);
            assertThat(stored.period()).isEqualTo(EXPECTED_PERIOD);
            assertThat(stored.createdAt()).isEqualTo(FIXED_INSTANT);
        }

        @Test
        @DisplayName("when the period is well-formed - then the answer is that same period")
        void whenPeriodIsWellFormed_thenTheAnswerIsThatSamePeriod() {
            IncomingMessageId reference =
                    IncomingMessageId.of(java.util.UUID.randomUUID().toString());
            stubStoredUserAndCreatedQuery(USER_ID, reference);

            SpendingPeriod answer = useCase.summarize(newCommand(reference, "2026-07-27", "2026-08-02"));

            assertThat(answer).isEqualTo(EXPECTED_PERIOD);
        }

        @Test
        @DisplayName(
                "when the stored user's id differs from the external id - then the stored query carries " + "that id")
        void whenStoredUsersIdDiffersFromExternalId_thenStoredQueryCarriesStoredUsersId() {
            long differentUserId = 42L;
            IncomingMessageId reference =
                    IncomingMessageId.of(java.util.UUID.randomUUID().toString());
            stubStoredUserAndCreatedQuery(differentUserId, reference);

            useCase.summarize(newCommand(reference, "2026-07-27", "2026-08-02"));

            assertThat(capturedQuery().userId()).isEqualTo(differentUserId);
        }

        @Test
        @DisplayName("when the spending query repository raises PersistenceFailedException - then the "
                + "exception propagates unchanged")
        void whenSpendingQueryRepositoryThrowsPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            when(userRepository.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(User.stored(USER_ID, EXTERNAL_ID)));
            PersistenceFailedException failure = new PersistenceFailedException("write failed", new RuntimeException());
            when(spendingQueryRepository.create(any())).thenThrow(failure);
            SummarizeSpendingCommand command = newCommand(
                    IncomingMessageId.of(java.util.UUID.randomUUID().toString()), "2026-07-27", "2026-08-02");

            assertThatThrownBy(() -> useCase.summarize(command)).isSameAs(failure);
        }
    }
}
