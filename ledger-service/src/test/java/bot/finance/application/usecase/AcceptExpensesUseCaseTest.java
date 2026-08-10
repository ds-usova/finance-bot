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

import bot.finance.application.dto.AcceptExpensesCommand;
import bot.finance.application.dto.ClearEmptiedReportsCommand;
import bot.finance.application.dto.ExpenseAcceptance;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.ReportClearingDispatchPort;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseAcceptanceException;
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

class AcceptExpensesUseCaseTest {

    private static final long USER_ID = 1L;
    private static final String EXTERNAL_ID = "555";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-05T00:00:00Z");
    private static final IncomingMessageId MESSAGE_A = IncomingMessageId.of("777:1");
    private static final IncomingMessageId MESSAGE_B = IncomingMessageId.of("777:2");

    private UserRepository userRepository;
    private ExpenseProposalRepository expenseProposalRepository;
    private ReportClearingDispatchPort reportClearingDispatchPort;
    private AcceptExpensesUseCase useCase;

    @BeforeEach
    void setUp() {
        Logger log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(AcceptExpensesUseCase.class)).thenReturn(log);
        userRepository = mock(UserRepository.class);
        expenseProposalRepository = mock(ExpenseProposalRepository.class);
        reportClearingDispatchPort = mock(ReportClearingDispatchPort.class);
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        useCase = new AcceptExpensesUseCase(
                userRepository, expenseProposalRepository, reportClearingDispatchPort, clock, loggerFactory);
    }

    private AcceptExpensesCommand commandFor(List<Long> ids) {
        return new AcceptExpensesCommand(new AuthenticatedUserId(EXTERNAL_ID), ProposalIds.of(ids));
    }

    private void stubStoredUser() {
        when(userRepository.requireByExternalId(EXTERNAL_ID)).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
    }

    @Nested
    @DisplayName("accepting expenses")
    class Accept {

        @Test
        @DisplayName("when the move answers one id per proposal for two posted - then accepted is 2 and missing is 0")
        void whenMoveAnswersOneIdPerProposalForTwoPosted_thenAcceptedTwoAndMissingZero() {
            stubStoredUser();
            when(expenseProposalRepository.acceptByIds(eq(USER_ID), any(), eq(FIXED_INSTANT)))
                    .thenReturn(List.of(MESSAGE_A, MESSAGE_B));

            ExpenseAcceptance result = useCase.accept(commandFor(List.of(1L, 2L)));

            assertThat(result.accepted()).isEqualTo(2);
            assertThat(result.missing()).isEqualTo(0);

            ArgumentCaptor<ProposalIds> idsCaptor = ArgumentCaptor.forClass(ProposalIds.class);
            verify(expenseProposalRepository).acceptByIds(eq(USER_ID), idsCaptor.capture(), eq(FIXED_INSTANT));
            assertThat(idsCaptor.getValue().ids()).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("when the move answers nothing for two posted - then accepted is 0 and missing is 2")
        void whenMoveAnswersNothingForTwoPosted_thenAcceptedZeroMissingTwoAndClearingNeverDispatched() {
            stubStoredUser();
            when(expenseProposalRepository.acceptByIds(eq(USER_ID), any(), eq(FIXED_INSTANT)))
                    .thenReturn(List.of());

            ExpenseAcceptance result = useCase.accept(commandFor(List.of(1L, 2L)));

            assertThat(result.accepted()).isEqualTo(0);
            assertThat(result.missing()).isEqualTo(2);
            verifyNoInteractions(reportClearingDispatchPort);
        }

        @Test
        @DisplayName("when the move answers one id for three posted - then accepted plus missing equals three")
        void whenMoveAnswersOneIdForThreePosted_thenAcceptedPlusMissingEqualsThree() {
            stubStoredUser();
            when(expenseProposalRepository.acceptByIds(eq(USER_ID), any(), eq(FIXED_INSTANT)))
                    .thenReturn(List.of(MESSAGE_A));

            ExpenseAcceptance result = useCase.accept(commandFor(List.of(1L, 2L, 3L)));

            assertThat(result.accepted() + result.missing()).isEqualTo(3);
        }

        @Test
        @DisplayName("when the move answers rows on two messages - then the dispatched command carries each "
                + "message once")
        void
                whenMoveAnswersTwoRowsOnOneMessageAndOneOnAnother_thenDispatchedCommandCarriesStoredIdAndEachMessageOnce() {
            stubStoredUser();
            when(expenseProposalRepository.acceptByIds(eq(USER_ID), any(), eq(FIXED_INSTANT)))
                    .thenReturn(List.of(MESSAGE_A, MESSAGE_A, MESSAGE_B));

            useCase.accept(commandFor(List.of(1L, 2L, 3L)));

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
        @DisplayName("when no user is stored for the caller's external id - then EntityNotFoundException is thrown")
        void whenNoUserRowForCallersExternalId_thenEntityNotFoundExceptionThrownAndNothingMovedOrDispatched() {
            when(userRepository.requireByExternalId(EXTERNAL_ID))
                    .thenThrow(new EntityNotFoundException("user", "no user stored under external id " + EXTERNAL_ID));

            assertThatThrownBy(() -> useCase.accept(commandFor(List.of(1L))))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(expenseProposalRepository);
            verifyNoInteractions(reportClearingDispatchPort);
        }

        @Test
        @DisplayName("when the repository throws PersistenceFailedException - then it propagates and nothing is "
                + "dispatched")
        void whenRepositoryThrowsPersistenceFailedException_thenExceptionPropagatesAndNothingDispatched() {
            stubStoredUser();
            PersistenceFailedException failure = new PersistenceFailedException("move failed", new RuntimeException());
            when(expenseProposalRepository.acceptByIds(eq(USER_ID), any(), eq(FIXED_INSTANT)))
                    .thenThrow(failure);

            assertThatThrownBy(() -> useCase.accept(commandFor(List.of(1L)))).isSameAs(failure);

            verifyNoInteractions(reportClearingDispatchPort);
        }

        @Test
        @DisplayName("when the command is null - then the module's own absent-argument exception is thrown and no "
                + "port is touched")
        void whenCommandIsNull_thenInvalidExpenseAcceptanceExceptionThrownAndNoPortTouched() {
            assertThatThrownBy(() -> useCase.accept(null)).isInstanceOf(InvalidExpenseAcceptanceException.class);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(expenseProposalRepository);
            verifyNoInteractions(reportClearingDispatchPort);
        }
    }
}
