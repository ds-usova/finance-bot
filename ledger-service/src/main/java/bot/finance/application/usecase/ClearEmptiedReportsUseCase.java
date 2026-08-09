package bot.finance.application.usecase;

import bot.finance.application.dto.ClearEmptiedReportsCommand;
import bot.finance.application.port.ClearEmptiedReportsPort;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import java.time.Clock;

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
        // narrows the command's messages to the ones findWithPendingProposals says still hold a pending
        // proposal, and for every other message looks up every report recorded for it and clears its buttons;
        // a report that cannot be cleared is skipped, not fatal to the rest. Logs one info line per message it
        // cleared and a warn for each one it could not (D17)
    }
}
