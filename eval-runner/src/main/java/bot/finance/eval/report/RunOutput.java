package bot.finance.eval.report;

import bot.finance.eval.Attempt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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

    private final Path file;
    private final String runId;
    private final Map<String, Object> config;
    private final Map<String, String> blocked;
    private final Instant startedAt = Instant.now();
    private final long startNanos = System.nanoTime();
    private final List<Attempt> attempts = new ArrayList<>();

    public RunOutput(Path file, String runId, Map<String, Object> config, Map<String, String> blocked) {
        this.file = file;
        this.runId = runId;
        this.config = config;
        this.blocked = blocked;
    }

    public void record(Attempt attempt) {
        attempts.add(attempt);
        write(document("incomplete"));
    }

    /** Writes the run as complete and answers its summary. */
    public Map<String, Object> finish() {
        Map<String, Object> document = document("complete");
        write(document);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) document.get("summary");
        return summary;
    }

    public Path file() {
        return file;
    }

    public static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    private Map<String, Object> document(String status) {
        long runMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", 1);
        document.put("runId", runId);
        document.put("status", status);
        document.put("startedAt", startedAt.toString());
        document.put("finishedAt", Instant.now().toString());
        document.put("config", config);
        document.put("summary", Summary.of(attempts, blocked, runMs));
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
