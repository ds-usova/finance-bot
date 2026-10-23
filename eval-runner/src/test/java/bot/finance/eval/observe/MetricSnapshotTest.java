package bot.finance.eval.observe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricSnapshotTest {

    private static final String BODY =
            """
            # HELP gen_ai_client_operation_seconds
            # TYPE gen_ai_client_operation_seconds summary
            gen_ai_client_operation_seconds_count{error="none",gen_ai_operation_name="chat",gen_ai_system="openai"} 4
            gen_ai_client_operation_seconds_count{error="none",gen_ai_operation_name="embedding",gen_ai_system="openai"} 2
            gen_ai_client_token_usage_total{gen_ai_token_type="input"} 1200.0
            gen_ai_client_token_usage_total{gen_ai_token_type="output"} 80.0
            gen_ai_client_token_usage_total{gen_ai_token_type="total"} 1280.0
            process_uptime_seconds 12.5
            """;

    @Test
    void sumsTheSeriesWhoseLabelsAgree() {
        MetricSnapshot snapshot = MetricSnapshot.parse(BODY);

        assertThat(snapshot.sum("gen_ai_client_operation_seconds_count", Map.of("gen_ai_operation_name", "chat")))
                .isEqualTo(4.0);
        assertThat(snapshot.sum("gen_ai_client_operation_seconds_count", Map.of()))
                .isEqualTo(6.0);
        assertThat(snapshot.sum("process_uptime_seconds", Map.of())).isEqualTo(12.5);
    }

    @Test
    void aLabelTheSeriesDoesNotCarryDoesNotExcludeIt() {
        MetricSnapshot snapshot = MetricSnapshot.parse(BODY);

        assertThat(snapshot.sum(
                        "gen_ai_client_token_usage_total",
                        Map.of("gen_ai_token_type", "input", "gen_ai_operation_name", "chat")))
                .isEqualTo(1200.0);
    }

    @Test
    void listsTheDistinctValuesOfALabelAcrossTheSeriesOfAName() {
        MetricSnapshot snapshot = MetricSnapshot.parse(
                """
                ai_turns_total{mode="tools",outcome="acted_on"} 3
                ai_turns_total{mode="tools",outcome="unavailable"} 1
                ai_turns_total{mode="classifier",outcome="acted_on"} 2
                """);

        assertThat(snapshot.labelValues("ai_turns_total", "mode")).containsExactlyInAnyOrder("tools", "classifier");
    }

    @Test
    void aNameWithNoSeriesHasNoLabelValues() {
        assertThat(MetricSnapshot.parse(BODY).labelValues("ai_turns_total", "mode"))
                .isEmpty();
    }

    @Test
    void aSeriesWithoutTheLabelContributesNoValue() {
        MetricSnapshot snapshot =
                MetricSnapshot.parse("""
                ai_turns_total{outcome="acted_on"} 3
                """);

        assertThat(snapshot.labelValues("ai_turns_total", "mode")).isEmpty();
    }

    @Test
    void anAbsentMeterSumsToZero() {
        assertThat(MetricSnapshot.parse(BODY).sum("ledger_mcp_tool_calls_total", Map.of()))
                .isZero();
    }
}
