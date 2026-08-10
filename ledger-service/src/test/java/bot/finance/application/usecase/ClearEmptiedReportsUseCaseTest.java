package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ClearEmptiedReportsCommand;
import bot.finance.application.dto.ReportLocation;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.domain.exception.InvalidProposalReportException;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ProposalReport;
import bot.finance.domain.value.IncomingMessageId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ClearEmptiedReportsUseCaseTest {

    private static final long USER_ID = 1L;
    private static final IncomingMessageId MESSAGE_A = IncomingMessageId.of("777:1");
    private static final IncomingMessageId MESSAGE_B = IncomingMessageId.of("777:2");

    private ExpenseProposalRepository expenseProposalRepository;
    private ProposalReportRepository proposalReportRepository;
    private MessageDeliveryPort messageDeliveryPort;
    private Logger log;
    private ClearEmptiedReportsUseCase useCase;

    @BeforeEach
    void setUp() {
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(ClearEmptiedReportsUseCase.class)).thenReturn(log);
        expenseProposalRepository = mock(ExpenseProposalRepository.class);
        proposalReportRepository = mock(ProposalReportRepository.class);
        messageDeliveryPort = mock(MessageDeliveryPort.class);
        useCase = new ClearEmptiedReportsUseCase(
                expenseProposalRepository, proposalReportRepository, messageDeliveryPort, loggerFactory);
    }

    private ClearEmptiedReportsCommand commandFor(List<IncomingMessageId> messageIds) {
        return new ClearEmptiedReportsCommand(USER_ID, messageIds);
    }

    private ProposalReport reportOn(long id, IncomingMessageId messageId, String sentMessageId) {
        return ProposalReport.stored(id, USER_ID, messageId, "777", sentMessageId);
    }

    @Nested
    @DisplayName("clearing emptied reports")
    class Clear {

        @Test
        @DisplayName("when the only message is emptied and holds one report - then its buttons come off")
        void whenMessageEmptiedWithOneReport_thenButtonsComeOffThatReportAndNothingElseSent() {
            when(expenseProposalRepository.findWithPendingProposals(eq(USER_ID), any()))
                    .thenReturn(Set.of());
            ProposalReport report = reportOn(10L, MESSAGE_A, "42");
            when(proposalReportRepository.findByIncomingMessageId(USER_ID, MESSAGE_A))
                    .thenReturn(List.of(report));

            useCase.clear(commandFor(List.of(MESSAGE_A)));

            verify(messageDeliveryPort).clearButtons(new ReportLocation("777", "42"));
        }

        @Test
        @DisplayName("when the one message still has a pending proposal - then nothing is sent for it")
        void whenMessageStillHasPendingProposal_thenNothingIsSentForIt() {
            when(expenseProposalRepository.findWithPendingProposals(eq(USER_ID), any()))
                    .thenReturn(Set.of(MESSAGE_A));

            useCase.clear(commandFor(List.of(MESSAGE_A)));

            verifyNoInteractions(proposalReportRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when one of two messages is emptied and the other still pending - then only the emptied one's "
                + "report is cleared")
        void whenOneOfTwoMessagesEmptiedAndOtherPending_thenOnlyEmptiedOnesReportCleared() {
            when(expenseProposalRepository.findWithPendingProposals(eq(USER_ID), any()))
                    .thenReturn(Set.of(MESSAGE_B));
            ProposalReport report = reportOn(10L, MESSAGE_A, "42");
            when(proposalReportRepository.findByIncomingMessageId(USER_ID, MESSAGE_A))
                    .thenReturn(List.of(report));

            useCase.clear(commandFor(List.of(MESSAGE_A, MESSAGE_B)));

            verify(proposalReportRepository).findByIncomingMessageId(USER_ID, MESSAGE_A);
            verify(proposalReportRepository, never()).findByIncomingMessageId(USER_ID, MESSAGE_B);
            verify(messageDeliveryPort).clearButtons(new ReportLocation("777", "42"));
        }

        @Test
        @DisplayName("when an emptied message has two reports recorded - then the buttons come off both, in the "
                + "order they were recorded")
        void whenEmptiedMessageHasTwoReports_thenButtonsComeOffBothInRecordedOrder() {
            when(expenseProposalRepository.findWithPendingProposals(eq(USER_ID), any()))
                    .thenReturn(Set.of());
            ProposalReport firstReport = reportOn(10L, MESSAGE_A, "42");
            ProposalReport secondReport = reportOn(11L, MESSAGE_A, "43");
            when(proposalReportRepository.findByIncomingMessageId(USER_ID, MESSAGE_A))
                    .thenReturn(List.of(firstReport, secondReport));

            useCase.clear(commandFor(List.of(MESSAGE_A)));

            InOrder order = inOrder(messageDeliveryPort);
            order.verify(messageDeliveryPort).clearButtons(new ReportLocation("777", "42"));
            order.verify(messageDeliveryPort).clearButtons(new ReportLocation("777", "43"));
        }

        @Test
        @DisplayName("when an emptied message has no report recorded - then nothing is sent, and the remaining "
                + "messages are still cleared")
        void whenEmptiedMessageHasNoReportRecorded_thenNothingSentAndRemainingMessagesStillCleared() {
            when(expenseProposalRepository.findWithPendingProposals(eq(USER_ID), any()))
                    .thenReturn(Set.of());
            when(proposalReportRepository.findByIncomingMessageId(USER_ID, MESSAGE_A))
                    .thenReturn(List.of());
            ProposalReport report = reportOn(11L, MESSAGE_B, "43");
            when(proposalReportRepository.findByIncomingMessageId(USER_ID, MESSAGE_B))
                    .thenReturn(List.of(report));

            useCase.clear(commandFor(List.of(MESSAGE_A, MESSAGE_B)));

            verify(messageDeliveryPort).clearButtons(new ReportLocation("777", "43"));
            verify(proposalReportRepository).findByIncomingMessageId(USER_ID, MESSAGE_B);
        }

        @Test
        @DisplayName("when the command is absent - then throws InvalidProposalReportException and no port is read")
        void whenCommandIsAbsent_thenThrowsInvalidProposalReportExceptionAndNoPortIsRead() {
            assertThatThrownBy(() -> useCase.clear(null)).isInstanceOf(InvalidProposalReportException.class);

            verifyNoInteractions(expenseProposalRepository);
            verifyNoInteractions(proposalReportRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when an emptied message has no report recorded - then it is not reported as cleared")
        void whenEmptiedMessageHasNoReportRecorded_thenItIsNotReportedAsCleared() {
            when(expenseProposalRepository.findWithPendingProposals(eq(USER_ID), any()))
                    .thenReturn(Set.of());
            when(proposalReportRepository.findByIncomingMessageId(USER_ID, MESSAGE_A))
                    .thenReturn(List.of());

            useCase.clear(commandFor(List.of(MESSAGE_A)));

            // Nothing is sent either way, so what the run says about it is the only thing that can be wrong:
            // a message with no report reads as one whose buttons came off.
            verifyNoInteractions(messageDeliveryPort);
            verify(log).debug(any(), eq(MESSAGE_A));
            verify(log, never()).info(any(), any());
        }

        @Test
        @DisplayName("when the counts read throws PersistenceFailedException - then nothing is sent, and nothing "
                + "propagates out of clear()")
        void whenCountsReadThrowsPersistenceFailedException_thenNothingSentAndNothingPropagates() {
            when(expenseProposalRepository.findWithPendingProposals(eq(USER_ID), any()))
                    .thenThrow(new PersistenceFailedException("read failed", new RuntimeException()));

            assertThatCode(() -> useCase.clear(commandFor(List.of(MESSAGE_A)))).doesNotThrowAnyException();

            verifyNoInteractions(proposalReportRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when the report lookup fails for the first message - then the second is still cleared")
        void whenReportLookupFailsForFirstMessage_thenSecondIsStillClearedAndNothingPropagates() {
            when(expenseProposalRepository.findWithPendingProposals(eq(USER_ID), any()))
                    .thenReturn(Set.of());
            when(proposalReportRepository.findByIncomingMessageId(USER_ID, MESSAGE_A))
                    .thenThrow(new PersistenceFailedException("read failed", new RuntimeException()));
            ProposalReport second = reportOn(11L, MESSAGE_B, "43");
            when(proposalReportRepository.findByIncomingMessageId(USER_ID, MESSAGE_B))
                    .thenReturn(List.of(second));

            assertThatCode(() -> useCase.clear(commandFor(List.of(MESSAGE_A, MESSAGE_B))))
                    .doesNotThrowAnyException();

            verify(messageDeliveryPort).clearButtons(new ReportLocation("777", "43"));
            verify(messageDeliveryPort, never()).clearButtons(new ReportLocation("777", "42"));
        }

        @Test
        @DisplayName("when the first of two clearings is refused - then the second is still attempted")
        void whenFirstClearingRefused_thenSecondStillAttemptedAndNothingPropagates() {
            when(expenseProposalRepository.findWithPendingProposals(eq(USER_ID), any()))
                    .thenReturn(Set.of());
            ProposalReport firstReport = reportOn(10L, MESSAGE_A, "42");
            ProposalReport secondReport = reportOn(11L, MESSAGE_A, "43");
            when(proposalReportRepository.findByIncomingMessageId(USER_ID, MESSAGE_A))
                    .thenReturn(List.of(firstReport, secondReport));
            doThrow(new MessageDeliveryFailedException("delivery failed"))
                    .when(messageDeliveryPort)
                    .clearButtons(new ReportLocation("777", "42"));

            assertThatCode(() -> useCase.clear(commandFor(List.of(MESSAGE_A)))).doesNotThrowAnyException();

            verify(messageDeliveryPort).clearButtons(new ReportLocation("777", "43"));
        }

        @Test
        @DisplayName("when the command carries no message ids - then neither repository is read and nothing is sent")
        void whenCommandCarriesNoMessageIds_thenNeitherRepositoryReadAndNothingSent() {
            useCase.clear(commandFor(List.of()));

            verifyNoInteractions(expenseProposalRepository);
            verifyNoInteractions(proposalReportRepository);
            verifyNoInteractions(messageDeliveryPort);
        }
    }
}
