package bot.finance.eval.execution;

import bot.finance.eval.Attempt;
import bot.finance.eval.EvalException;
import bot.finance.eval.ledger.LedgerClient;
import bot.finance.eval.ledger.Proposal;
import bot.finance.eval.ledger.WrittenMessage;
import bot.finance.eval.observe.McpCallLog;
import bot.finance.eval.observe.TargetMetrics;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * The message a case is judged on: filed inside a metrics window, timed to its terminal outcome, and inspected
 * for the proposals and MCP calls it produced.
 */
final class TargetTurn {

    private static final String STAGE = "target";
    private static final Duration LOG_FLUSH = Duration.ofSeconds(1);

    private final LedgerClient ledger;
    private final TargetMetrics metrics;
    private final McpCallLog mcpCallLog;
    private final Duration terminalTimeout;
    private final Attempt attempt;

    private LocalDate runDate;
    private Long messageId;
    private Stopwatch turn;
    private long logMark;
    private TargetMetrics.Window window;
    private boolean inspected;

    TargetTurn(
            LedgerClient ledger,
            TargetMetrics metrics,
            McpCallLog mcpCallLog,
            Duration terminalTimeout,
            Attempt attempt) {
        this.ledger = ledger;
        this.metrics = metrics;
        this.mcpCallLog = mcpCallLog;
        this.terminalTimeout = terminalTimeout;
        this.attempt = attempt;
    }

    /** The UTC date captured before filing, which the case's date tokens resolve from; null until filed. */
    LocalDate runDate() {
        return runDate;
    }

    boolean inspected() {
        return inspected;
    }

    void file(String text) {
        runDate = LocalDate.now(ZoneOffset.UTC);
        attempt.expectedDate = runDate.toString();
        window = metrics.open(e -> attempt.warnings.add(Attempt.Failure.of(e)));
        logMark = mcpCallLog == null ? 0 : mcpCallLog.mark();
        messageId = ledger.fileMessage(STAGE, text);
        turn = Stopwatch.start();
        attempt.messageIds.put(STAGE, messageId);
    }

    void awaitOutcome() {
        long id = filedId();
        WrittenMessage done = Poller.await(
                STAGE,
                terminalTimeout,
                () -> ledger.message(STAGE, id),
                WrittenMessage::done,
                "the target did not reach a terminal outcome");
        attempt.measurements.targetTurnMs = turn.elapsedMs();
        attempt.actual.outcome = done.outcome();
    }

    /** Reads the target's proposals, leaving out {@code learningProposals}, then its usage and MCP calls. */
    void inspect(Set<Long> learningProposals) {
        long id = filedId();
        List<Proposal> proposals = ledger.pendingProposals("inspection").stream()
                .filter(proposal -> !learningProposals.contains(proposal.id()))
                .toList();
        attempt.actual.proposals = proposals;
        attempt.measurements.usage = window.close();
        if (mcpCallLog != null) {
            Poller.sleep(LOG_FLUSH);
            attempt.actual.mcpCalls = mcpCallLog.callsSince(logMark, WrittenMessage.incomingMessageId(id));
        }
        inspected = true;
    }

    private long filedId() {
        if (messageId == null) {
            throw new EvalException("setup", "the target message was never filed");
        }
        return messageId;
    }
}
