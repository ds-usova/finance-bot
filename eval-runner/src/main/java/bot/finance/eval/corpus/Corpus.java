package bot.finance.eval.corpus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Reads {@code cases/*.yaml} and the steps file each one names, in the corpus's default order. */
public final class Corpus {

    private static final YAMLMapper YAML = new YAMLMapper();

    private Corpus() {}

    public static List<EvalCase> load(Path corpusDir) {
        Path casesDir = corpusDir.resolve("cases");
        List<EvalCase> cases = new ArrayList<>();
        try (Stream<Path> files = Files.list(casesDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                cases.add(read(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + casesDir, e);
        }
        cases.sort(Comparator.comparing(EvalCase::id, Corpus::compareIds));
        return cases;
    }

    /** Dimension, then local number, both numerically: {@code 2-001} precedes {@code 10-001}. */
    static int compareIds(String left, String right) {
        String[] l = left.split("-", 3);
        String[] r = right.split("-", 3);
        int byDimension = Integer.compare(Integer.parseInt(l[0]), Integer.parseInt(r[0]));
        return byDimension != 0 ? byDimension : Integer.compare(Integer.parseInt(l[1]), Integer.parseInt(r[1]));
    }

    private static EvalCase read(Path caseFile) {
        JsonNode node = readYaml(caseFile);
        String id = node.path("id").asString();
        JsonNode steps =
                readYaml(caseFile.getParent().resolve(node.path("steps").asString()));
        if (!id.equals(steps.path("caseId").asString())) {
            throw new IllegalStateException("steps file of " + id + " names case " + steps.path("caseId"));
        }

        List<LearningEntry> learning = new ArrayList<>();
        for (JsonNode entry : node.path("learning")) {
            learning.add(new LearningEntry(
                    entry.path("id").asString(),
                    entry.path("message").asString(),
                    entry.path("proposalCount").asInt(),
                    entry.path("classification").path("grouping").asString(),
                    entry.path("classification").path("category").asString(),
                    entry.path("decision").asString()));
        }

        String blockedReason = "blocked".equals(node.path("status").asString(null))
                ? node.path("blockedReason").asString("blocked")
                : null;

        return new EvalCase(
                id,
                node.path("covers").asString(),
                node.path("message").asString(),
                node.path("notes").asString(null),
                learning,
                node.path("expected"),
                node.get("executionLimits"),
                blockedReason,
                steps(steps.path("steps")),
                steps(steps.path("finally")));
    }

    private static List<Step> steps(JsonNode list) {
        List<Step> steps = new ArrayList<>();
        for (JsonNode step : list) {
            steps.add(new Step(
                    step.path("action").asString(),
                    step.path("message").asString(null),
                    step.path("learningId").asString(null)));
        }
        return steps;
    }

    private static JsonNode readYaml(Path file) {
        try {
            return YAML.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}
