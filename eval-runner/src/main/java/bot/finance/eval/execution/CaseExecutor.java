package bot.finance.eval.execution;

import bot.finance.eval.Attempt;
import bot.finance.eval.EvalException;
import bot.finance.eval.RunnerConfig;
import bot.finance.eval.corpus.EvalCase;
import bot.finance.eval.corpus.LearningEntry;
import bot.finance.eval.corpus.Step;
import bot.finance.eval.ledger.LedgerClient;
import bot.finance.eval.observe.McpCallLog;
import bot.finance.eval.observe.MemoryProbe;
import bot.finance.eval.observe.TargetMetrics;
import bot.finance.eval.oracle.Constraint;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Runs one attempt of a case by dispatching each action of its steps file, then judges it. */
public final class CaseExecutor {

    private final RunnerConfig config;
    private final HttpClient http;
    private final TargetMetrics metrics;
    private final MemoryProbe memoryProbe;
    private final McpCallLog mcpCallLog;

    public CaseExecutor(
            RunnerConfig config,
            HttpClient http,
            TargetMetrics metrics,
            MemoryProbe memoryProbe,
            McpCallLog mcpCallLog) {
        this.config = config;
        this.http = http;
        this.metrics = metrics;
        this.memoryProbe = memoryProbe;
        this.mcpCallLog = mcpCallLog;
    }

    public Attempt run(EvalCase evalCase, int repetition, int attemptNumber) {
        return new Run(evalCase, repetition, attemptNumber).execute();
    }

    private final class Run {

        private final EvalCase evalCase;
        private final Attempt attempt = new Attempt();
        private LearningSteps learning;
        private TargetTurn target;

        Run(EvalCase evalCase, int repetition, int attemptNumber) {
            this.evalCase = evalCase;
            attempt.caseId = evalCase.id();
            attempt.covers = evalCase.covers();
            attempt.caseText = new Attempt.CaseText(
                    evalCase.message(), evalCase.notes(), evalCase.learning(), evalCase.expected());
            attempt.repetition = repetition;
            attempt.attempt = attemptNumber;
        }

        Attempt execute() {
            attempt.startedAt = Instant.now().toString();
            try {
                evalCase.steps().forEach(this::perform);
                if (target == null || !target.inspected()) {
                    throw new EvalException("inspection", "the steps never filed and inspected a target message");
                }
                AttemptEvaluator.evaluate(evalCase.expected(), target.runDate(), attempt);
            } catch (RuntimeException e) {
                attempt.errors.add(Attempt.Failure.of(e));
                attempt.verdict = "error";
            }
            if (rolledOver()) {
                attempt.attemptStatus = "discarded-date-rollover";
                attempt.verdict = null;
            }
            attempt.finishedAt = Instant.now().toString();
            return attempt;
        }

        private void perform(Step step) {
            switch (step.action()) {
                case "verify-fixture" -> openSession();
                case "file-learning-message" -> learning().file(entry(step));
                case "wait-for-terminal-outcome" -> {
                    if ("target".equals(step.message())) {
                        target().awaitOutcome();
                    } else {
                        learning().awaitOutcome(step.message());
                    }
                }
                case "verify-learning-proposals" -> learning().verifyProposals(entry(step));
                case "set-learning-classification" -> learning().classify(entry(step));
                case "resolve-learning-proposals" -> learning().resolve(entry(step));
                case "wait-for-memory-readiness" -> learning().awaitReadiness(entry(step));
                case "verify-call-budget-enforcement" ->
                    throw new EvalException("setup", "call-budget enforcement is unavailable");
                case "file-target-message" -> target().file(evalCase.message());
                case "inspect-application-state" -> target().inspect(learning().proposalIds());
                // MCP calls are read during inspection; recall evidence is deferred for the demo.
                case "inspect-mcp-calls", "collect-recall-evidence" -> {}
                default -> throw new EvalException("setup", "unknown action " + step.action());
            }
        }

        private void openSession() {
            LedgerClient ledger = Fixture.newPerson(http, config.ledgerUrl(), config.botToken());
            learning = new LearningSteps(
                    ledger, memoryProbe, config.terminalTimeout(), config.readinessTimeout(), attempt);
            target = new TargetTurn(ledger, metrics, mcpCallLog, config.terminalTimeout(), attempt);
        }

        private LearningSteps learning() {
            if (learning == null) {
                throw new EvalException("setup", "a step ran before verify-fixture");
            }
            return learning;
        }

        private TargetTurn target() {
            learning();
            return target;
        }

        private LearningEntry entry(Step step) {
            return evalCase.learningEntry(step.learningId())
                    .orElseThrow(() -> new EvalException("setup", "no learning entry " + step.learningId()));
        }

        private boolean rolledOver() {
            return target != null
                    && target.runDate() != null
                    && Constraint.namesDateToken(evalCase.expected())
                    && !target.runDate().equals(LocalDate.now(ZoneOffset.UTC));
        }
    }
}
