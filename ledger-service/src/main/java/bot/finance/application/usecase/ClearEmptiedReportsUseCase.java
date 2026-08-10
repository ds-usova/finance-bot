package bot.finance.application.usecase;

import bot.finance.application.dto.ClearEmptiedReportsCommand;
import bot.finance.application.dto.ReportLocation;
import bot.finance.application.port.ClearEmptiedReportsPort;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ProposalReport;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Clock;
import java.util.List;
import java.util.Set;

public class ClearEmptiedReportsUseCase implements ClearEmptiedReportsPort {

    private final ExpenseProposalRepository expenseProposalRepository;
    private final ProposalReportRepository proposalReportRepository;
    private final MessageDeliveryPort messageDeliveryPort;
    private final Clock clock;
    private final Logger log;

    public ClearEmptiedReportsUseCase(
            ExpenseProposalRepository expenseProposalRepository,
            ProposalReportRepository proposalReportRepository,
            MessageDeliveryPort messageDeliveryPort,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.expenseProposalRepository = expenseProposalRepository;
        this.proposalReportRepository = proposalReportRepository;
        this.messageDeliveryPort = messageDeliveryPort;
        this.clock = clock;
        this.log = loggerFactory.getLogger(ClearEmptiedReportsUseCase.class);
    }

    @Override
    public void clear(ClearEmptiedReportsCommand command) {
        List<IncomingMessageId> messageIds = command.incomingMessageIds();
        if (messageIds.isEmpty()) {
            return;
        }

        Set<IncomingMessageId> stillPending;
        try {
            stillPending = expenseProposalRepository.findWithPendingProposals(command.userId(), messageIds);
        } catch (PersistenceFailedException e) {
            log.warn("failed to read pending proposal counts for user {}: {}", command.userId(), e.getMessage());
            return;
        }

        for (IncomingMessageId messageId : messageIds) {
            if (!stillPending.contains(messageId)) {
                clearReportsFor(command.userId(), messageId);
            }
        }
    }

    private void clearReportsFor(long userId, IncomingMessageId messageId) {
        List<ProposalReport> reports = proposalReportRepository.findByIncomingMessageId(userId, messageId);
        boolean allCleared = true;
        for (ProposalReport report : reports) {
            try {
                messageDeliveryPort.clearButtons(new ReportLocation(report.conversationId(), report.sentMessageId()));
            } catch (MessageDeliveryFailedException e) {
                allCleared = false;
                log.warn("failed to clear report for message {}: {}", messageId, e.getMessage());
            }
        }
        if (allCleared) {
            log.info("cleared report for message {}", messageId);
        }
    }
}
