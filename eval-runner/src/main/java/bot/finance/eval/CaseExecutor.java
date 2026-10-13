package bot.finance.eval;

import bot.finance.eval.corpus.EvalCase;
import bot.finance.eval.corpus.LearningEntry;
import bot.finance.eval.corpus.Step;
import bot.finance.eval.ledger.Category;
import bot.finance.eval.ledger.LedgerClient;
import bot.finance.eval.ledger.Proposal;
import bot.finance.eval.ledger.WrittenMessage;
import bot.finance.eval.observe.McpCallLog;
import bot.finance.eval.observe.MemoryProbe;
import bot.finance.eval.observe.MetricSnapshot;
import bot.finance.eval.observe.Prometheus;
import bot.finance.eval.oracle.Constraint;
import bot.finance.eval.oracle.Evaluation;
import bot.finance.eval.oracle.Oracle;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import tools.jackson.databind.JsonNode;

/**
 * Runs one attempt of a case through its steps file. Each attempt signs in a new person, so no state of another
 * case can be recalled, and nothing is cleaned up afterwards.
 */
final class CaseExecutor {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration LOG_FLUSH = Duration.ofSeconds(1);
    private static final Set<String> FAILED_OUTCOMES = Set.of("PARTIAL", "FAILED");

    private final RunnerConfig config;
    private final HttpClient http;
    private final Prometheus prometheus;
    private final MemoryProbe memoryProbe;
    private final McpCallLog mcpCallLog;

    CaseExecutor(
            RunnerConfig config,
            HttpClient http,
            Prometheus prometheus,
            MemoryProbe memoryProbe,
            McpCallLog mcpCallLog) {
        this.config = config;
        this.http = http;
        this.prometheus = prometheus;
        this.memoryProbe = memoryProbe;
        this.mcpCallLog = mcpCallLog;
    }

    Attempt run(EvalCase evalCase, int repetition, int attemptNumber) {
        return new Run(evalCase, repetition, attemptNumber).execute();
    }

    private final class Run {

        private final EvalCase evalCase;
        private final Attempt attempt = new Attempt();
        private final Set<Long> learningProposalIds = new HashSet<>();
        private final Map<String, List<Long>> proposalsByLearning = new HashMap<>();
        private final Map<String, Long> filedAt = new HashMap<>();
        private final Map<String, Long> decidedAt = new HashMap<>();

        private LedgerClient ledger;
        private LocalDate runDate;
        private Long targetId;
        private long logMark;
        private MetricSnapshot ledgerBefore;
        private MetricSnapshot connectorBefore;
        private List<Proposal> targetProposals;

        Run(EvalCase evalCase, int repetition, int attemptNumber) {
            this.evalCase = evalCase;
            attempt.caseId = evalCase.id();
            attempt.covers = evalCase.covers();
            attempt.repetition = repetition;
            attempt.attempt = attemptNumber;
        }

        Attempt execute() {
            attempt.startedAt = Instant.now().toString();
            try {
                for (Step step : evalCase.steps()) {
                    perform(step);
                }
                evaluate();
            } catch (RuntimeException e) {
                attempt.errors.add(Attempt.Failure.of(e));
                attempt.verdict = "error";
            }
            if (runDate != null
                    && Constraint.namesDateToken(evalCase.expected())
                    && !runDate.equals(LocalDate.now(ZoneOffset.UTC))) {
                attempt.attemptStatus = "discarded-date-rollover";
                attempt.verdict = null;
            }
            attempt.finishedAt = Instant.now().toString();
            return attempt;
        }

        private void perform(Step step) {
            switch (step.action()) {
                case "verify-fixture" -> verifyFixture();
                case "file-learning-message" -> fileLearningMessage(learning(step));
                case "wait-for-terminal-outcome" -> waitForTerminalOutcome(step.message());
                case "verify-learning-proposals" -> verifyLearningProposals(learning(step));
                case "set-learning-classification" -> setLearningClassification(learning(step));
                case "resolve-learning-proposals" -> resolveLearningProposals(learning(step));
                case "wait-for-memory-readiness" -> waitForMemoryReadiness(learning(step));
                case "verify-call-budget-enforcement" ->
                    throw new EvalException("setup", "call-budget enforcement is unavailable");
                case "file-target-message" -> fileTargetMessage();
                case "inspect-application-state" -> inspectApplicationState();
                // MCP calls are read for every case that declares them; recall evidence is deferred for the demo.
                case "inspect-mcp-calls", "collect-recall-evidence" -> {}
                default -> throw new EvalException("setup", "unknown action " + step.action());
            }
        }

        private void verifyFixture() {
            ledger = new LedgerClient(http, config.ledgerUrl());
            long telegramId = ThreadLocalRandom.current().nextLong(8_000_000_000L, 9_000_000_000L);
            ledger.signIn(telegramId, config.botToken());
            ledger.setDefaultCurrency("HUF");
            long pending = ledger.pendingTotal();
            if (pending != 0) {
                throw new EvalException("setup", "a new person already has " + pending + " pending entries");
            }
        }

        private void fileLearningMessage(LearningEntry entry) {
            long id = ledger.fileMessage("learning", entry.message());
            filedAt.put(entry.id(), System.nanoTime());
            attempt.messageIds.put(entry.id(), id);
        }

        private void waitForTerminalOutcome(String message) {
            if ("target".equals(message)) {
                WrittenMessage done = poll("target", targetId);
                attempt.measurements.targetTurnMs = elapsedMs(filedAt.get("target"));
                attempt.actual.outcome = done.outcome();
                return;
            }
            Long id = attempt.messageIds.get(message);
            if (id == null) {
                throw new EvalException("setup", "no learning message " + message + " was filed");
            }
            WrittenMessage done = poll("learning", id);
            learningMeasurement(message).turnMs = elapsedMs(filedAt.get(message));
            if (!"RECORDED".equals(done.outcome())) {
                throw new EvalException("learning", "learning message " + message + " ended " + done.outcome());
            }
        }

        private void verifyLearningProposals(LearningEntry entry) {
            List<Long> ids = new ArrayList<>();
            for (JsonNode item : ledger.allPending("learning")) {
                long id = item.path("id").asLong();
                if (!learningProposalIds.contains(id)) {
                    ids.add(id);
                }
            }
            if (ids.size() != entry.proposalCount()) {
                throw new EvalException(
                        "learning",
                        "learning message " + entry.id() + " proposed " + ids.size() + ", not "
                                + entry.proposalCount());
            }
            learningProposalIds.addAll(ids);
            proposalsByLearning.put(entry.id(), ids);
        }

        private void setLearningClassification(LearningEntry entry) {
            Category category = ledger.categories("learning").stream()
                    .filter(c -> c.name().equals(entry.category())
                            && c.groupingName().equals(entry.grouping()))
                    .findFirst()
                    .orElseThrow(() ->
                            new EvalException("setup", "no category " + entry.grouping() + " / " + entry.category()));
            for (long id : learningProposals(entry)) {
                ledger.changeCategory("learning", id, category.id());
            }
        }

        private void resolveLearningProposals(LearningEntry entry) {
            if (entry.approves()) {
                ledger.accept("learning", learningProposals(entry));
            } else {
                ledger.discard("learning", learningProposals(entry));
            }
            decidedAt.put(entry.id(), System.nanoTime());
        }

        private void waitForMemoryReadiness(LearningEntry entry) {
            String incomingMessageId = WrittenMessage.incomingMessageId(attempt.messageIds.get(entry.id()));
            String status = entry.approves() ? "ACCEPTED" : "DISCARDED";
            List<Long> ids = learningProposals(entry);
            await(
                    "memory-readiness",
                    config.readinessTimeout(),
                    () -> memoryProbe.ready(incomingMessageId, ids, status),
                    "learning message " + entry.id() + " was not available to recall");
            learningMeasurement(entry.id()).availabilityMs = elapsedMs(decidedAt.get(entry.id()));
        }

        private void fileTargetMessage() {
            runDate = LocalDate.now(ZoneOffset.UTC);
            attempt.expectedDate = runDate.toString();
            ledgerBefore = scrape(config.ledgerMetricsUrl());
            connectorBefore = scrape(config.connectorMetricsUrl());
            logMark = mcpCallLog == null ? 0 : mcpCallLog.mark();
            targetId = ledger.fileMessage("target", evalCase.message());
            filedAt.put("target", System.nanoTime());
            attempt.messageIds.put("target", targetId);
        }

        private void inspectApplicationState() {
            Map<Long, Category> categories = new HashMap<>();
            ledger.categories("inspection").forEach(category -> categories.put(category.id(), category));
            List<Proposal> proposals = new ArrayList<>();
            for (JsonNode item : ledger.allPending("inspection")) {
                long id = item.path("id").asLong();
                if (learningProposalIds.contains(id)) {
                    continue;
                }
                Category category = categories.get(item.path("categoryId").asLong());
                JsonNode merchant = item.path("merchant");
                proposals.add(new Proposal(
                        id,
                        item.path("money").path("amount").asString(),
                        item.path("money").path("currency").asString(),
                        merchant.isNull() || merchant.isMissingNode() ? null : merchant.asString(),
                        item.path("description").asString(),
                        item.path("categoryId").asLong(),
                        category == null ? null : category.name(),
                        category == null ? null : category.groupingName()));
            }
            targetProposals = proposals;
            attempt.actual.proposals = proposals;
            measureTarget(scrape(config.ledgerMetricsUrl()), scrape(config.connectorMetricsUrl()));
        }

        private void evaluate() {
            if (targetId == null || targetProposals == null) {
                throw new EvalException("inspection", "the steps never filed and inspected a target message");
            }
            if (mcpCallLog != null) {
                sleep(LOG_FLUSH);
                attempt.actual.mcpCalls = mcpCallLog.callsSince(logMark, WrittenMessage.incomingMessageId(targetId));
            }
            JsonNode expected = Constraint.resolved(evalCase.expected(), runDate);
            attempt.expected = expected;

            Evaluation evaluation =
                    Oracle.evaluate(expected, attempt.actual.outcome, targetProposals, attempt.actual.mcpCalls);
            attempt.checks = evaluation.checks();
            attempt.matching = new Attempt.Matching();
            attempt.matching.expected = evaluation.expectedCount();
            attempt.matching.actual = evaluation.actualCount();
            attempt.matching.matched = evaluation.matchedCount();
            attempt.matching.pairs = evaluation.matchedPairs();
            attempt.verdict = evaluation.verdict();

            if (FAILED_OUTCOMES.contains(attempt.actual.outcome)) {
                attempt.errors.add(new Attempt.Failure(
                        "processing", "outcome", "the target ended " + attempt.actual.outcome, null));
                attempt.verdict = "error";
            } else if (evaluation.verdict().equals("error")) {
                attempt.errors.add(new Attempt.Failure(
                        "inspection", "evidence", "a declared MCP expectation has no call record", null));
            }
            if (!attempt.verdict.equals("error")) {
                attempt.qualityMetrics.precision = evaluation.precision();
                attempt.qualityMetrics.recall = evaluation.recall();
                attempt.qualityMetrics.f1 = evaluation.f1();
            }
        }

        private void measureTarget(MetricSnapshot ledgerAfter, MetricSnapshot connectorAfter) {
            Attempt.Measurements m = attempt.measurements;
            if (connectorBefore != null && connectorAfter != null) {
                m.providerChatCalls = delta(
                        connectorBefore,
                        connectorAfter,
                        "gen_ai_client_operation_seconds_count",
                        Map.of("gen_ai_operation_name", "chat"));
                m.providerChatSeconds = delta(
                        connectorBefore,
                        connectorAfter,
                        "gen_ai_client_operation_seconds_sum",
                        Map.of("gen_ai_operation_name", "chat"));
                m.providerEmbeddingCalls = delta(
                        connectorBefore,
                        connectorAfter,
                        "gen_ai_client_operation_seconds_count",
                        Map.of("gen_ai_operation_name", "embedding"));
                m.inputTokens = delta(
                        connectorBefore,
                        connectorAfter,
                        "gen_ai_client_token_usage_total",
                        Map.of("gen_ai_token_type", "input", "gen_ai_operation_name", "chat"));
                m.outputTokens = delta(
                        connectorBefore,
                        connectorAfter,
                        "gen_ai_client_token_usage_total",
                        Map.of("gen_ai_token_type", "output", "gen_ai_operation_name", "chat"));
            }
            if (ledgerBefore != null && ledgerAfter != null) {
                m.ledgerToolCalls = delta(ledgerBefore, ledgerAfter, "ledger_mcp_tool_calls_total", Map.of());
            }
        }

        private MetricSnapshot scrape(String url) {
            if (url == null || url.isBlank()) {
                return null;
            }
            try {
                return prometheus.scrape(url);
            } catch (EvalException e) {
                attempt.warnings.add(Attempt.Failure.of(e));
                return null;
            }
        }

        private WrittenMessage poll(String stage, long id) {
            WrittenMessage[] last = new WrittenMessage[1];
            await(
                    stage,
                    config.terminalTimeout(),
                    () -> (last[0] = ledger.message(stage, id)).done(),
                    "message " + id + " did not reach a terminal outcome");
            return last[0];
        }

        private List<Long> learningProposals(LearningEntry entry) {
            List<Long> ids = proposalsByLearning.get(entry.id());
            if (ids == null) {
                throw new EvalException("setup", "proposals of " + entry.id() + " were never verified");
            }
            return ids;
        }

        private LearningEntry learning(Step step) {
            return evalCase.learningEntry(step.learningId())
                    .orElseThrow(() -> new EvalException("setup", "no learning entry " + step.learningId()));
        }

        private Attempt.Learning learningMeasurement(String learningId) {
            return attempt.measurements.learning.computeIfAbsent(learningId, id -> new Attempt.Learning());
        }
    }

    private static void await(String stage, Duration timeout, BooleanSupplier condition, String timeoutMessage) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new EvalException(stage, timeoutMessage + " within " + timeout.toSeconds() + "s");
            }
            sleep(POLL_INTERVAL);
        }
    }

    private static double delta(MetricSnapshot before, MetricSnapshot after, String name, Map<String, String> labels) {
        return after.sum(name, labels) - before.sum(name, labels);
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EvalException("runner", "interrupted");
        }
    }
}
