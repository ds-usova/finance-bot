package bot.finance.eval.observe;

import bot.finance.eval.EvalException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Scrapes both services before the target is filed and again after it is inspected, and reads the meters' growth
 * in between. A failed scrape is reported, never fatal: it leaves its service's values null.
 */
public final class TargetMetrics {

    private static final Map<String, String> CHAT = Map.of("gen_ai_operation_name", "chat");

    private static final String LEDGER_TURN_SERVICE = "bot.finance.ledger.v1.LedgerTurnService";

    private final Prometheus prometheus;
    private final String ledgerUrl;
    private final String connectorUrl;

    public TargetMetrics(Prometheus prometheus, String ledgerUrl, String connectorUrl) {
        this.prometheus = prometheus;
        this.ledgerUrl = ledgerUrl;
        this.connectorUrl = connectorUrl;
    }

    public Window open(Consumer<EvalException> onFailure) {
        return new Window(scrape(ledgerUrl, onFailure), scrape(connectorUrl, onFailure), onFailure);
    }

    public final class Window {

        private final MetricSnapshot ledgerBefore;
        private final MetricSnapshot connectorBefore;
        private final Consumer<EvalException> onFailure;

        private Window(MetricSnapshot ledgerBefore, MetricSnapshot connectorBefore, Consumer<EvalException> onFailure) {
            this.ledgerBefore = ledgerBefore;
            this.connectorBefore = connectorBefore;
            this.onFailure = onFailure;
        }

        public TargetUsage close() {
            MetricSnapshot ledgerAfter = scrape(ledgerUrl, onFailure);
            MetricSnapshot connectorAfter = scrape(connectorUrl, onFailure);
            Growth connector = new Growth(connectorBefore, connectorAfter);
            Growth ledger = new Growth(ledgerBefore, ledgerAfter);
            return new TargetUsage(
                    connector.of("gen_ai_client_operation_seconds_count", CHAT),
                    connector.of("gen_ai_client_operation_seconds_sum", CHAT),
                    connector.of("gen_ai_client_operation_seconds_count", Map.of("gen_ai_operation_name", "embedding")),
                    connector.of(
                            "gen_ai_client_token_usage_total",
                            Map.of("gen_ai_token_type", "input", "gen_ai_operation_name", "chat")),
                    connector.of(
                            "gen_ai_client_token_usage_total",
                            Map.of("gen_ai_token_type", "output", "gen_ai_operation_name", "chat")),
                    ledgerToolCalls(ledger),
                    extractionMode(connector),
                    connector.of("ai_classifier_seconds_count", Map.of()),
                    connector.of("ai_classifier_seconds_sum", Map.of()));
        }

        private Double ledgerToolCalls(Growth ledger) {
            Double toolCalls = ledger.of("ledger_mcp_tool_calls_total", Map.of());
            if (toolCalls == null || toolCalls > 0) {
                return toolCalls;
            }
            return ledger.of("grpc_server_seconds_count", Map.of("rpc_service", LEDGER_TURN_SERVICE));
        }

        private String extractionMode(Growth connector) {
            if (connector.before() == null || connector.after() == null) {
                return null;
            }
            List<String> grown = connector.after().labelValues("ai_turns_total", "mode").stream()
                    .filter(mode -> connector.of("ai_turns_total", Map.of("mode", mode)) > 0)
                    .toList();
            return grown.size() == 1 ? grown.get(0) : null;
        }
    }

    private MetricSnapshot scrape(String url, Consumer<EvalException> onFailure) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return prometheus.scrape(url);
        } catch (EvalException e) {
            onFailure.accept(e);
            return null;
        }
    }

    private record Growth(MetricSnapshot before, MetricSnapshot after) {

        Double of(String name, Map<String, String> labels) {
            return before == null || after == null ? null : after.sum(name, labels) - before.sum(name, labels);
        }
    }
}
