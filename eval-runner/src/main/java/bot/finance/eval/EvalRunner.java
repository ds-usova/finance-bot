package bot.finance.eval;

import bot.finance.eval.corpus.Corpus;
import bot.finance.eval.corpus.EvalCase;
import bot.finance.eval.observe.McpCallLog;
import bot.finance.eval.observe.MemoryProbe;
import bot.finance.eval.observe.Prometheus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the corpus against a running ledger and AI Connector and writes {@code <out>/<label>.json}; or, given
 * {@code compare <before.json> <after.json>}, prints the two runs side by side.
 */
public final class EvalRunner {

    static final JsonMapper JSON =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private static final int MAX_ATTEMPTS = 3;

    private EvalRunner() {}

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("compare")) {
            if (args.length != 3) {
                throw new IllegalArgumentException("usage: compare <before.json> <after.json>");
            }
            System.out.print(Comparison.render(read(Path.of(args[1])), read(Path.of(args[2]))));
            return;
        }
        run(RunnerConfig.from(args, System.getenv()));
    }

    private static void run(RunnerConfig config) {
        List<EvalCase> selected = Corpus.load(config.corpusDir()).stream()
                .filter(c -> config.casePrefixes().isEmpty()
                        || config.casePrefixes().stream().anyMatch(c.id()::startsWith))
                .toList();

        Map<String, String> blocked = new LinkedHashMap<>();
        List<EvalCase> runnable = new ArrayList<>();
        for (EvalCase evalCase : selected) {
            if (evalCase.blocked()) {
                blocked.put(evalCase.id(), evalCase.blockedReason());
            } else if (evalCase.executionLimits() != null && !config.budgetEnforced()) {
                blocked.put(evalCase.id(), "declares executionLimits; call-budget enforcement is unavailable");
            } else {
                runnable.add(evalCase);
            }
        }

        HttpClient http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        CaseExecutor executor = new CaseExecutor(
                config,
                http,
                new Prometheus(http),
                new MemoryProbe(config.aiDbUrl(), config.aiDbUser(), config.aiDbPassword()),
                config.ledgerLog() == null ? null : new McpCallLog(config.ledgerLog()));

        Instant started = Instant.now();
        long startNanos = System.nanoTime();
        String runId = config.label() + "-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                        .withZone(ZoneOffset.UTC)
                        .format(started);
        Path out = config.outDir().resolve(config.label() + ".json");
        List<Attempt> attempts = new ArrayList<>();

        System.out.printf(
                "%s: %d cases x %d repetitions, %d blocked -> %s%n",
                runId, runnable.size(), config.repetitions(), blocked.size(), out);
        for (EvalCase evalCase : runnable) {
            for (int repetition = 1; repetition <= config.repetitions(); repetition++) {
                for (int number = 1; number <= MAX_ATTEMPTS; number++) {
                    Attempt attempt = executor.run(evalCase, repetition, number);
                    attempts.add(attempt);
                    print(attempt);
                    write(out, document(runId, config, started, startNanos, attempts, blocked, "incomplete"));
                    if (!attempt.attemptStatus.equals("discarded-date-rollover")) {
                        break;
                    }
                }
            }
        }

        Map<String, Object> document = document(runId, config, started, startNanos, attempts, blocked, "complete");
        write(out, document);
        System.out.println(JSON.writeValueAsString(document.get("summary")));
    }

    private static Map<String, Object> document(
            String runId,
            RunnerConfig config,
            Instant started,
            long startNanos,
            List<Attempt> attempts,
            Map<String, String> blocked,
            String status) {
        long runMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", 1);
        document.put("runId", runId);
        document.put("status", status);
        document.put("startedAt", started.toString());
        document.put("finishedAt", Instant.now().toString());
        document.put("config", config.redacted());
        document.put("summary", Summary.of(attempts, blocked, runMs));
        document.put("attempts", attempts);
        return document;
    }

    private static void print(Attempt attempt) {
        String errors =
                attempt.errors.isEmpty() ? "" : "  " + attempt.errors.getFirst().message();
        System.out.printf(
                "%-45s rep %d att %d  %-7s %-18s %6s ms%s%n",
                attempt.caseId,
                attempt.repetition,
                attempt.attempt,
                attempt.attemptStatus.equals("completed") ? attempt.verdict : "rollover",
                attempt.actual.outcome,
                attempt.measurements.targetTurnMs,
                errors);
    }

    private static void write(Path out, Map<String, Object> document) {
        try {
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.writeString(out, JSON.writeValueAsString(document));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + out, e);
        }
    }

    private static tools.jackson.databind.JsonNode read(Path file) {
        try {
            return JSON.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}
