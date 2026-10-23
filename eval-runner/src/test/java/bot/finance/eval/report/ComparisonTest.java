package bot.finance.eval.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ComparisonTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static JsonNode run(String runId, String extractionMode) {
        String mode = extractionMode == null ? "" : "\"extractionMode\": \"" + extractionMode + "\",";
        return JSON.readTree(
                """
                {"runId": "%s", "summary": {%s "quality": {"passRate": 0.5}}}
                """
                        .formatted(runId, mode));
    }

    /** The table's rows after the header and its rule, each as its trimmed cells. */
    private static List<List<String>> rows(String table) {
        return table.lines()
                .takeWhile(line -> !line.isBlank())
                .skip(2)
                .map(line -> Arrays.stream(line.substring(1).split("\\|", -1))
                        .map(String::trim)
                        .toList())
                .toList();
    }

    @Test
    void putsAnExtractionModeRowFirstAndFlagsDifferingModes() {
        List<List<String>> rows = rows(Comparison.render(run("a", "tools"), run("b", "classifier")));

        assertThat(rows.get(0)).startsWith("extraction mode", "tools", "classifier", "differs");
        assertThat(rows.get(1).get(0)).isEqualTo("pass rate");
    }

    @Test
    void leavesTheChangeEmptyWhereBothRunsRecordTheSameMode() {
        List<List<String>> rows = rows(Comparison.render(run("a", "classifier"), run("b", "classifier")));

        assertThat(rows.get(0)).startsWith("extraction mode", "classifier", "classifier", "");
    }

    @Test
    void showsNotAvailableWhereOneRunRecordedNoMode() {
        List<List<String>> rows = rows(Comparison.render(run("a", null), run("b", "classifier")));

        assertThat(rows.get(0)).startsWith("extraction mode", "n/a", "classifier");
        assertThat(rows.get(0).get(3)).isNotEqualTo("differs");
    }
}
