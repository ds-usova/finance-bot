package bot.finance.application.usecase;

import bot.finance.application.dto.TurnEnding;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.domain.model.IncomingTurn;

class IncomingTurnReport {

    private final IncomingTurnRepository incomingTurnRepository;
    private final ExpenseRepository expenseRepository;
    private final SpendingQueryRepository spendingQueryRepository;
    private final MessageDeliveryPort messageDeliveryPort;
    private final ProposalReportRepository proposalReportRepository;
    private final TurnMeters turnMeters;
    private final Logger log;

    IncomingTurnReport(
            IncomingTurnRepository incomingTurnRepository,
            ExpenseRepository expenseRepository,
            SpendingQueryRepository spendingQueryRepository,
            MessageDeliveryPort messageDeliveryPort,
            ProposalReportRepository proposalReportRepository,
            TurnMeters turnMeters,
            Logger log) {
        this.incomingTurnRepository = incomingTurnRepository;
        this.expenseRepository = expenseRepository;
        this.spendingQueryRepository = spendingQueryRepository;
        this.messageDeliveryPort = messageDeliveryPort;
        this.proposalReportRepository = proposalReportRepository;
        this.turnMeters = turnMeters;
        this.log = log;
    }

    boolean settle(IncomingTurn turn, TurnEnding ending) {
        // stub-intent: for an end or a refused hand-over. Reads the proposals and spending summaries under the
        // turn's message, then deletes the turn; only when that delete removed it: logs at error an ending other
        // than acted on, naming the message and the outcome, delivers the report the ending and what was read earn,
        // stores its location, counts the turn under its outcome and discards the periods a delivered report
        // carried, logging a failed discard at warn; a report that cannot be delivered counts the turn unreported.
        // Answers whether this call settled it
        return false;
    }

    boolean settleSwept(IncomingTurn turn) {
        // stub-intent: for the sweep. Settles the turn as settle does for a failed ending, writing no error line
        return false;
    }
}
