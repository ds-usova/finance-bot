package bot.finance.eval.execution;

import bot.finance.eval.Attempt;
import bot.finance.eval.EvalException;
import bot.finance.eval.corpus.LearningEntry;
import bot.finance.eval.ledger.Category;
import bot.finance.eval.ledger.LedgerClient;
import bot.finance.eval.ledger.WrittenMessage;
import bot.finance.eval.observe.MemoryProbe;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The learning half of a memory case: each entry's message is filed, its proposals are classified and decided as
 * the case says, and the decision is awaited in the AI Connector's memory.
 */
final class LearningSteps {

    private static final String STAGE = "learning";

    private final LedgerClient ledger;
    private final MemoryProbe memoryProbe;
    private final Duration terminalTimeout;
    private final Duration readinessTimeout;
    private final Attempt attempt;
    private final Map<String, Stopwatch> turns = new HashMap<>();
    private final Map<String, Stopwatch> decisions = new HashMap<>();
    private final Map<String, List<Long>> proposals = new HashMap<>();
    private final Set<Long> proposalIds = new HashSet<>();

    LearningSteps(
            LedgerClient ledger,
            MemoryProbe memoryProbe,
            Duration terminalTimeout,
            Duration readinessTimeout,
            Attempt attempt) {
        this.ledger = ledger;
        this.memoryProbe = memoryProbe;
        this.terminalTimeout = terminalTimeout;
        this.readinessTimeout = readinessTimeout;
        this.attempt = attempt;
    }

    /** Every proposal a learning message produced, which no target expectation may count. */
    Set<Long> proposalIds() {
        return proposalIds;
    }

    void file(LearningEntry entry) {
        long id = ledger.fileMessage(STAGE, entry.message());
        turns.put(entry.id(), Stopwatch.start());
        attempt.messageIds.put(entry.id(), id);
    }

    void awaitOutcome(String learningId) {
        long id = messageId(learningId);
        WrittenMessage done = Poller.await(
                STAGE,
                terminalTimeout,
                () -> ledger.message(STAGE, id),
                WrittenMessage::done,
                "learning message " + learningId + " did not reach a terminal outcome");
        attempt.measurements.learning(learningId).turnMs = turns.get(learningId).elapsedMs();
        if (!"RECORDED".equals(done.outcome())) {
            throw new EvalException(STAGE, "learning message " + learningId + " ended " + done.outcome());
        }
    }

    void verifyProposals(LearningEntry entry) {
        List<Long> ids = ledger.pendingIds(STAGE).stream()
                .filter(id -> !proposalIds.contains(id))
                .toList();
        if (ids.size() != entry.proposalCount()) {
            throw new EvalException(
                    STAGE,
                    "learning message " + entry.id() + " proposed " + ids.size() + ", not " + entry.proposalCount());
        }
        proposalIds.addAll(ids);
        proposals.put(entry.id(), ids);
    }

    void classify(LearningEntry entry) {
        Category category = ledger.category(STAGE, entry.grouping(), entry.category())
                .orElseThrow(
                        () -> new EvalException("setup", "no category " + entry.grouping() + " / " + entry.category()));
        for (long id : proposalsOf(entry)) {
            ledger.changeCategory(STAGE, id, category.id());
        }
    }

    void resolve(LearningEntry entry) {
        if (entry.approves()) {
            ledger.accept(STAGE, proposalsOf(entry));
        } else {
            ledger.discard(STAGE, proposalsOf(entry));
        }
        decisions.put(entry.id(), Stopwatch.start());
    }

    void awaitReadiness(LearningEntry entry) {
        String incomingMessageId = WrittenMessage.incomingMessageId(messageId(entry.id()));
        String status = entry.approves() ? "ACCEPTED" : "DISCARDED";
        List<Long> ids = proposalsOf(entry);
        Poller.await(
                "memory-readiness",
                readinessTimeout,
                () -> memoryProbe.ready(incomingMessageId, ids, status),
                Boolean::booleanValue,
                "learning message " + entry.id() + " was not available to recall");
        attempt.measurements.learning(entry.id()).availabilityMs =
                decisions.get(entry.id()).elapsedMs();
    }

    private long messageId(String learningId) {
        Long id = attempt.messageIds.get(learningId);
        if (id == null) {
            throw new EvalException("setup", "no learning message " + learningId + " was filed");
        }
        return id;
    }

    private List<Long> proposalsOf(LearningEntry entry) {
        List<Long> ids = proposals.get(entry.id());
        if (ids == null) {
            throw new EvalException("setup", "proposals of " + entry.id() + " were never verified");
        }
        return ids;
    }
}
