package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ClearEmptiedReportsCommand;
import bot.finance.application.dto.DiscardExpensesCommand;
import bot.finance.application.dto.ExpenseDiscard;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.ReportClearingDispatchPort;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseDiscardException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.ProposalIds;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DiscardExpensesUseCaseTest {

    private static final long USER_ID = 1L;
    private static final String EXTERNAL_ID = "555";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-05T00:00:00Z");
    private static final IncomingMessageId MESSAGE_A = IncomingMessageId.of("777:1");
    private static final IncomingMessageId MESSAGE_B = IncomingMessageId.of("777:2");

    private UserRepository userRepository;
    private ExpenseRepository expenseRepository;
    private ReportClearingDispatchPort reportClearingDispatchPort;
    private DiscardExpensesUseCase useCase;

    @BeforeEach
    void setUp() {
        Logger log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(DiscardExpensesUseCase.class)).thenReturn(log);
        userRepository = mock(UserRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        reportClearingDispatchPort = mock(ReportClearingDispatchPort.class);
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        useCase = new DiscardExpensesUseCase(
                userRepository, expenseRepository, reportClearingDispatchPort, clock, loggerFactory);
    }

    private DiscardExpensesCommand commandFor(List<Long> ids) {
        return new DiscardExpensesCommand(new AuthenticatedUserId(USER_ID), ProposalIds.of(ids));
    }

    private void stubStoredUser() {
        when(userRepository.requireById(USER_ID)).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
    }

    @Nested
    @DisplayName("discarding expenses")
    class Discard {

        @Test
        @DisplayName("when the repository answers one message per posted id - then discarded is 2 and missing is 0")
        void whenRepositoryAnswersOneMessagePerPostedId_thenDiscardedTwoAndMissingZero() {
            stubStoredUser();
            when(expenseRepository.discardByIds(eq(USER_ID), any(), eq(FIXED_INSTANT)))
                    .thenReturn(List.of(MESSAGE_A, MESSAGE_B));

            ExpenseDiscard result = useCase.discard(commandFor(List.of(1L, 2L)));

            assertThat(result.discarded()).isEqualTo(2);
            assertThat(result.missing()).isEqualTo(0);

            ArgumentCaptor<ProposalIds> idsCaptor = ArgumentCaptor.forClass(ProposalIds.class);
            verify(expenseRepository).discardByIds(eq(USER_ID), idsCaptor.capture(), eq(FIXED_INSTANT));
            assertThat(idsCaptor.getValue().ids()).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("when the repository answers nothing for two posted ids - then discarded is 0 and missing is 2")
        void whenRepositoryAnswersNothingForTwoPostedIds_thenDiscardedZeroMissingTwoAndNothingCleared() {
            stubStoredUser();
            when(expenseRepository.discardByIds(eq(USER_ID), any(), eq(FIXED_INSTANT)))
                    .thenReturn(List.of());

            ExpenseDiscard result = useCase.discard(commandFor(List.of(1L, 2L)));

            assertThat(result.discarded()).isEqualTo(0);
            assertThat(result.missing()).isEqualTo(2);
            verifyNoInteractions(reportClearingDispatchPort);
        }

        @Test
        @DisplayName("when the repository answers rows on two messages - then the clearing gets each message once")
        void whenRepositoryAnswersRowsOnTwoMessages_thenClearingReceivesEachMessageOnce() {
            stubStoredUser();
            when(expenseRepository.discardByIds(eq(USER_ID), any(), eq(FIXED_INSTANT)))
                    .thenReturn(List.of(MESSAGE_A, MESSAGE_A, MESSAGE_B));

            useCase.discard(commandFor(List.of(1L, 2L, 3L)));

            ArgumentCaptor<ClearEmptiedReportsCommand> dispatchCaptor =
                    ArgumentCaptor.forClass(ClearEmptiedReportsCommand.class);
            verify(reportClearingDispatchPort, atLeastOnce()).dispatch(dispatchCaptor.capture());
            List<ClearEmptiedReportsCommand> dispatched = dispatchCaptor.getAllValues();
            assertThat(dispatched).allMatch(command -> command.userId() == USER_ID);
            List<IncomingMessageId> dispatchedMessages = dispatched.stream()
                    .flatMap(command -> command.incomingMessageIds().stream())
                    .toList();
            assertThat(dispatchedMessages).containsExactlyInAnyOrder(MESSAGE_A, MESSAGE_B);
        }

        @Test
        @DisplayName("when no user is stored for the caller - then EntityNotFoundException is thrown")
        void whenNoUserRowForCaller_thenEntityNotFoundExceptionThrownAndNothingCalled() {
            when(userRepository.requireById(USER_ID))
                    .thenThrow(new EntityNotFoundException("user", "no user stored under external id " + EXTERNAL_ID));

            assertThatThrownBy(() -> useCase.discard(commandFor(List.of(1L))))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(expenseRepository);
            verifyNoInteractions(reportClearingDispatchPort);
        }

        @Test
        @DisplayName("when the repository throws PersistenceFailedException - then it propagates and nothing is "
                + "cleared")
        void whenRepositoryThrowsPersistenceFailedException_thenExceptionPropagatesAndNothingCleared() {
            stubStoredUser();
            PersistenceFailedException failure =
                    new PersistenceFailedException("discard failed", new RuntimeException());
            when(expenseRepository.discardByIds(eq(USER_ID), any(), eq(FIXED_INSTANT)))
                    .thenThrow(failure);

            assertThatThrownBy(() -> useCase.discard(commandFor(List.of(1L)))).isSameAs(failure);

            verifyNoInteractions(reportClearingDispatchPort);
        }

        @Test
        @DisplayName("when the command is null - then InvalidExpenseDiscardException is thrown and no port is called")
        void whenCommandIsNull_thenInvalidExpenseDiscardExceptionThrownAndNoPortCalled() {
            assertThatThrownBy(() -> useCase.discard(null)).isInstanceOf(InvalidExpenseDiscardException.class);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(expenseRepository);
            verifyNoInteractions(reportClearingDispatchPort);
        }
    }
}
