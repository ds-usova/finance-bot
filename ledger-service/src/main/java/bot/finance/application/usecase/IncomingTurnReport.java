package bot.finance.application.usecase;

import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.ReportLocation;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.SpendingSummary;
import bot.finance.application.dto.TurnEnding;
import bot.finance.application.dto.TurnReport;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.IncomingTurn;
import bot.finance.domain.model.ProposalReport;
import bot.finance.domain.value.IncomingMessageId;
import java.util.List;
import java.util.Optional;

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
        return settle(turn, ending, true);
    }

    boolean settleSwept(IncomingTurn turn) {
        return settle(turn, TurnEnding.FAILED, false);
    }

    private boolean settle(IncomingTurn turn, TurnEnding ending, boolean writeErrorLine) {
        long userId = turn.userId();
        IncomingMessageId reference = turn.reference();
        List<ProposalSummary> proposals = expenseRepository.findSummariesByMessageReference(userId, reference);
        List<SpendingSummary> summaries = spendingSummaries(userId, reference);
        if (!incomingTurnRepository.delete(turn.id().orElseThrow())) {
            return false;
        }
        ReportOutcome outcome = outcomeOf(ending, proposals, summaries);
        if (writeErrorLine && ending != TurnEnding.ACTED_ON) {
            log.error("turn for message {} ended {} and was reported as {}", reference, ending, outcome);
        }
        deliver(turn, outcome, proposals, summaries);
        return true;
    }

    private void deliver(
            IncomingTurn turn,
            ReportOutcome outcome,
            List<ProposalSummary> proposals,
            List<SpendingSummary> summaries) {
        IncomingMessageId reference = turn.reference();
        Optional<ReportLocation> location;
        try {
            location = messageDeliveryPort.deliver(new TurnReport(
                    turn.conversationId(), turn.inboundMessageId(), outcome, proposals, summaries, reference));
        } catch (MessageDeliveryFailedException e) {
            log.warn("failed to deliver report for message {}: {}", reference, e.getMessage());
            turnMeters.countUnreported();
            return;
        }
        location.ifPresent(stored -> storeReport(turn, stored));
        turnMeters.countTurn(outcome);
        if (!summaries.isEmpty()) {
            new ExtractionTurn(spendingQueryRepository, log).discardPeriods(turn.userId(), reference);
        }
    }

    private void storeReport(IncomingTurn turn, ReportLocation location) {
        try {
            proposalReportRepository.store(ProposalReport.newProposalReport(
                    turn.userId(), turn.reference(), location.conversationId(), location.sentMessageId()));
        } catch (PersistenceFailedException e) {
            log.warn("failed to store report for message {}: {}", turn.reference(), e.getMessage());
        }
    }

    private List<SpendingSummary> spendingSummaries(long userId, IncomingMessageId reference) {
        return spendingQueryRepository.findPeriodsByMessageReference(userId, reference).stream()
                .map(period -> new SpendingSummary(period, expenseRepository.totalsByCurrency(userId, period)))
                .toList();
    }

    private ReportOutcome outcomeOf(
            TurnEnding ending, List<ProposalSummary> proposals, List<SpendingSummary> summaries) {
        if (ending != TurnEnding.ACTED_ON) {
            return proposals.isEmpty() && summaries.isEmpty() ? ReportOutcome.FAILED : ReportOutcome.PARTIAL;
        }
        if (!proposals.isEmpty()) {
            return ReportOutcome.RECORDED;
        }
        return summaries.isEmpty() ? ReportOutcome.NOTHING_IDENTIFIED : ReportOutcome.ANSWERED;
    }
}
