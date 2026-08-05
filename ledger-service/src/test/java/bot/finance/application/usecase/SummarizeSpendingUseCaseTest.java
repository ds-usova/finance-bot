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
import bot.finance.domain.value.MessageReference;
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

    private SummarizeSpendingCommand newCommand(MessageReference reference, String from, String to) {
        return new SummarizeSpendingCommand(new AuthenticatedUserId(EXTERNAL_ID), reference, from, to);
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
        @DisplayName("when the command's written dates do not make a period - then throws "
                + "InvalidSpendingPeriodException and neither repository is touched")
        void whenWrittenDatesDoNotMakeAPeriod_thenThrowsInvalidSpendingPeriodExceptionAndRepositoriesUntouched() {
            SummarizeSpendingCommand command = newCommand(MessageReference.newReference(), "not-a-date", "2026-08-05");

            assertThatThrownBy(() -> useCase.summarize(command)).isInstanceOf(InvalidSpendingPeriodException.class);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(spendingQueryRepository);
        }

        @Test
        @DisplayName("when nothing is stored under the command's external id - then throws "
                + "EntityNotFoundException and the spending query repository is never called")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundExceptionAndSpendingQueryRepositoryUntouched() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());
            SummarizeSpendingCommand command = newCommand(MessageReference.newReference(), "2026-08-01", "2026-08-05");

            assertThatThrownBy(() -> useCase.summarize(command)).isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(spendingQueryRepository);
        }

        @Test
        @DisplayName("when a stored user and a well-formed period are given - then the stored query carries "
                + "that user's stored id, the command's reference, the parsed period and the fixed clock's "
                + "instant, and the answer is that same period")
        void whenStoredUserAndWellFormedPeriod_thenStoredQueryCarriesUserReferencePeriodAndClockInstant() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(User.stored(USER_ID, EXTERNAL_ID)));
            MessageReference reference = MessageReference.newReference();
            SpendingPeriod expectedPeriod = new SpendingPeriod(LocalDate.parse("2026-07-27"), LocalDate.parse("2026-08-02"));
            when(spendingQueryRepository.create(any()))
                    .thenReturn(SpendingQuery.stored(9L, USER_ID, expectedPeriod, reference, FIXED_INSTANT));
            SummarizeSpendingCommand command = newCommand(reference, "2026-07-27", "2026-08-02");

            SpendingPeriod answer = useCase.summarize(command);

            ArgumentCaptor<SpendingQuery> captor = ArgumentCaptor.forClass(SpendingQuery.class);
            verify(spendingQueryRepository).create(captor.capture());
            SpendingQuery stored = captor.getValue();
            assertThat(stored.userId()).isEqualTo(USER_ID);
            assertThat(stored.messageReference()).isEqualTo(reference);
            assertThat(stored.period()).isEqualTo(expectedPeriod);
            assertThat(stored.createdAt()).isEqualTo(FIXED_INSTANT);
            assertThat(answer).isEqualTo(expectedPeriod);
        }

        @Test
        @DisplayName("when the stored user's id differs from the external id on the command - then the stored "
                + "query carries that stored user's id")
        void whenStoredUsersIdDiffersFromExternalId_thenStoredQueryCarriesStoredUsersId() {
            long differentUserId = 42L;
            when(userRepository.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(User.stored(differentUserId, EXTERNAL_ID)));
            MessageReference reference = MessageReference.newReference();
            SpendingPeriod expectedPeriod = new SpendingPeriod(LocalDate.parse("2026-07-27"), LocalDate.parse("2026-08-02"));
            when(spendingQueryRepository.create(any()))
                    .thenReturn(SpendingQuery.stored(9L, differentUserId, expectedPeriod, reference, FIXED_INSTANT));
            SummarizeSpendingCommand command = newCommand(reference, "2026-07-27", "2026-08-02");

            useCase.summarize(command);

            ArgumentCaptor<SpendingQuery> captor = ArgumentCaptor.forClass(SpendingQuery.class);
            verify(spendingQueryRepository).create(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(differentUserId);
        }

        @Test
        @DisplayName("when the spending query repository raises PersistenceFailedException - then the "
                + "exception propagates unchanged")
        void whenSpendingQueryRepositoryThrowsPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(User.stored(USER_ID, EXTERNAL_ID)));
            PersistenceFailedException failure = new PersistenceFailedException("write failed", new RuntimeException());
            when(spendingQueryRepository.create(any())).thenThrow(failure);
            SummarizeSpendingCommand command = newCommand(MessageReference.newReference(), "2026-07-27", "2026-08-02");

            assertThatThrownBy(() -> useCase.summarize(command)).isSameAs(failure);
        }
    }
}
