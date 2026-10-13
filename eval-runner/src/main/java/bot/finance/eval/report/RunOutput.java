package bot.finance.eval.report;

import bot.finance.eval.Attempt;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 * The run's output file. It is rewritten after every attempt, marked incomplete until the last one, so an
 * interrupted run keeps every attempt it finished.
 */
public final class RunOutput {

    private static final JsonMapper JSON =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private static final DateTimeFormatter RUN_ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Path file;
    private final String runId;
    private final Map<String, Object> config;
    private final Map<String, String> blocked;
    private final Instant startedAt = Instant.now();
    private final long startNanos = System.nanoTime();
    private final List<Attempt> attempts = new ArrayList<>();

    private RunOutput(Path file, String runId, Map<String, Object> config, Map<String, String> blocked) {
        this.file = file;
        this.runId = runId;
        this.config = config;
        this.blocked = blocked;
    }

    /** A run named {@code <label>-<UTC start>}, written to {@code <outDir>/<label>.json}. */
    public static RunOutput open(Path outDir, String label, Map<String, Object> config, Map<String, String> blocked) {
        String runId = label + "-" + RUN_ID_TIME.format(Instant.now());
        return new RunOutput(outDir.resolve(label + ".json"), runId, config, blocked);
    }

    public void record(Attempt attempt) {
        attempts.add(attempt);
        write(document("incomplete", summary()));
    }

    /** Writes the run as complete and answers its summary. */
    public Map<String, Object> finish() {
        Map<String, Object> summary = summary();
        write(document("complete", summary));
        return summary;
    }

    public String runId() {
        return runId;
    }

    public Path file() {
        return file;
    }

    public static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    private Map<String, Object> summary() {
        return Summary.of(
                attempts,
                blocked,
                Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
    }

    private Map<String, Object> document(String status, Map<String, Object> summary) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", 1);
        document.put("runId", runId);
        document.put("status", status);
        document.put("startedAt", startedAt.toString());
        document.put("finishedAt", Instant.now().toString());
        document.put("config", config);
        document.put("summary", summary);
        document.put("attempts", attempts);
        return document;
    }

    private void write(Map<String, Object> document) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, JSON.writeValueAsString(document));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }
}
