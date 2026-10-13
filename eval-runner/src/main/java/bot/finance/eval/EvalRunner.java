package bot.finance.eval;

import bot.finance.eval.corpus.CaseSelection;
import bot.finance.eval.corpus.Corpus;
import bot.finance.eval.corpus.EvalCase;
import bot.finance.eval.execution.CaseExecutor;
import bot.finance.eval.observe.McpCallLog;
import bot.finance.eval.observe.MemoryProbe;
import bot.finance.eval.observe.Prometheus;
import bot.finance.eval.observe.TargetMetrics;
import bot.finance.eval.report.Comparison;
import bot.finance.eval.report.ProgressLog;
import bot.finance.eval.report.RunOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the corpus against a running ledger and AI Connector and writes {@code <out>/<label>.json}; or, given
 * {@code compare <before.json> <after.json>}, writes the two runs side by side to {@code compare.md} beside the
 * second.
 */
public final class EvalRunner {

    private static final Logger LOG = LoggerFactory.getLogger(EvalRunner.class);
    private static final int MAX_ATTEMPTS = 3;

    private EvalRunner() {}

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("compare")) {
            if (args.length != 3) {
                throw new IllegalArgumentException("usage: compare <before.json> <after.json>");
            }
            compare(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        run(RunnerConfig.from(args, System.getenv()));
    }

    private static void run(RunnerConfig config) {
        CaseSelection selection =
                CaseSelection.of(Corpus.load(config.corpusDir()), config.casePrefixes(), config.budgetEnforced());
        CaseExecutor executor = executor(config);
        String runId = config.label() + "-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                        .withZone(ZoneOffset.UTC)
                        .format(Instant.now());
        RunOutput output = new RunOutput(
                config.outDir().resolve(config.label() + ".json"), runId, config.redacted(), selection.blocked());

        LOG.info(
                "{}: {} cases x {} repetitions, {} blocked, writing {}",
                runId,
                selection.runnable().size(),
                config.repetitions(),
                selection.blocked().size(),
                output.file());
        for (EvalCase evalCase : selection.runnable()) {
            for (int repetition = 1; repetition <= config.repetitions(); repetition++) {
                runRepetition(executor, output, evalCase, repetition);
            }
        }
        LOG.info("summary: {}", RunOutput.json(output.finish()));
    }

    /** One repetition, repeated with the next attempt number while a UTC rollover discards it. */
    private static void runRepetition(CaseExecutor executor, RunOutput output, EvalCase evalCase, int repetition) {
        for (int number = 1; number <= MAX_ATTEMPTS; number++) {
            Attempt attempt = executor.run(evalCase, repetition, number);
            output.record(attempt);
            ProgressLog.attempt(attempt);
            if (!attempt.attemptStatus.equals("discarded-date-rollover")) {
                return;
            }
        }
    }

    private static CaseExecutor executor(RunnerConfig config) {
        HttpClient http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new CaseExecutor(
                config,
                http,
                new TargetMetrics(new Prometheus(http), config.ledgerMetricsUrl(), config.connectorMetricsUrl()),
                new MemoryProbe(config.aiDbUrl(), config.aiDbUser(), config.aiDbPassword()),
                config.ledgerLog() == null ? null : new McpCallLog(config.ledgerLog()));
    }

    private static void compare(Path before, Path after) {
        String table = Comparison.render(read(before), read(after));
        Path out = after.toAbsolutePath().resolveSibling("compare.md");
        try {
            Files.writeString(out, table);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + out, e);
        }
        LOG.info("wrote {}\n{}", out, table);
    }

    private static JsonNode read(Path file) {
        try {
            return new JsonMapper().readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}
