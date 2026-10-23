package bot.finance.eval.observe;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.eval.EvalException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TargetMetricsTest {

    private static final String LEDGER_TURN_SERVICE = "bot.finance.ledger.v1.LedgerTurnService";

    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void readsToolsAsTheExtractionModeWhenItsTurnsGrow() {
        TargetUsage usage = connector(ok(turns("tools", 3)), ok(turns("tools", 4)));

        assertThat(usage.extractionMode()).isEqualTo("tools");
    }

    @Test
    void readsClassifierWhenItsSeriesAppearsDuringTheWindow() {
        TargetUsage usage = connector(ok("process_uptime_seconds 1\n"), ok(turns("classifier", 1)));

        assertThat(usage.extractionMode()).isEqualTo("classifier");
    }

    @Test
    void hasNoExtractionModeWhenNoTurnCounted() {
        TargetUsage usage = connector(ok(turns("tools", 3)), ok(turns("tools", 3)));

        assertThat(usage.extractionMode()).isNull();
    }

    @Test
    void hasNoExtractionModeWhenBothModesGrow() {
        TargetUsage usage = connector(
                ok(turns("tools", 3) + turns("classifier", 1)), ok(turns("tools", 4) + turns("classifier", 2)));

        assertThat(usage.extractionMode()).isNull();
    }

    @Test
    void sumsTheClassifierCallsAndSecondsOverEveryOutcome() {
        String before =
                """
                ai_classifier_seconds_count{outcome="answered"} 5
                ai_classifier_seconds_sum{outcome="answered"} 1.0
                ai_classifier_seconds_count{outcome="failed"} 1
                ai_classifier_seconds_sum{outcome="failed"} 0.5
                """;
        String after =
                """
                ai_classifier_seconds_count{outcome="answered"} 6
                ai_classifier_seconds_sum{outcome="answered"} 1.25
                ai_classifier_seconds_count{outcome="failed"} 2
                ai_classifier_seconds_sum{outcome="failed"} 1.0
                """;

        TargetUsage usage = connector(ok(before), ok(after));

        assertThat(usage.classifierCalls()).isEqualTo(2.0);
        assertThat(usage.classifierSeconds()).isEqualTo(0.75);
    }

    @Test
    void countsNoClassifierCallsWhenTheConnectorHasNoClassifierMeter() {
        TargetUsage usage = connector(ok(turns("tools", 3)), ok(turns("tools", 4)));

        assertThat(usage.classifierCalls()).isZero();
        assertThat(usage.classifierSeconds()).isZero();
    }

    @Test
    void countsTheLedgersTurnServiceRpcsWhenNoToolCallsWereCounted() {
        String before =
                """
                ledger_mcp_tool_calls_total 7
                %s
                %s
                %s
                """
                        .formatted(
                                rpc(LEDGER_TURN_SERVICE, "FileExpenseProposal", 10),
                                rpc(LEDGER_TURN_SERVICE, "RecordSpendingPeriod", 20),
                                rpc("bot.finance.ledger.v1.Other", "Ping", 30));
        String after =
                """
                ledger_mcp_tool_calls_total 7
                %s
                %s
                %s
                """
                        .formatted(
                                rpc(LEDGER_TURN_SERVICE, "FileExpenseProposal", 11),
                                rpc(LEDGER_TURN_SERVICE, "RecordSpendingPeriod", 21),
                                rpc("bot.finance.ledger.v1.Other", "Ping", 35));

        TargetUsage usage = ledger(ok(before), ok(after));

        assertThat(usage.ledgerToolCalls()).isEqualTo(2.0);
    }

    @Test
    void countsTheLedgersToolCallsWhenItHasNoRpcMeter() {
        TargetUsage usage = ledger(ok("ledger_mcp_tool_calls_total 4\n"), ok("ledger_mcp_tool_calls_total 7\n"));

        assertThat(usage.ledgerToolCalls()).isEqualTo(3.0);
    }

    @Test
    void aFailedConnectorScrapeNullsTheConnectorValuesAndIsReported() {
        List<EvalException> failures = new ArrayList<>();

        TargetUsage usage = run(
                failures::add,
                ok("ledger_mcp_tool_calls_total 1\n"),
                ok("ledger_mcp_tool_calls_total 3\n"),
                ok(turns("tools", 3)),
                new Reply(500, "boom"));

        assertThat(usage.extractionMode()).isNull();
        assertThat(usage.classifierCalls()).isNull();
        assertThat(usage.classifierSeconds()).isNull();
        assertThat(usage.providerChatCalls()).isNull();
        assertThat(usage.providerChatSeconds()).isNull();
        assertThat(usage.providerEmbeddingCalls()).isNull();
        assertThat(usage.inputTokens()).isNull();
        assertThat(usage.outputTokens()).isNull();
        assertThat(usage.ledgerToolCalls()).isEqualTo(2.0);
        assertThat(failures).hasSize(1);
    }

    @Test
    void aFailedLedgerScrapeNullsTheLedgerCountOnly() {
        TargetUsage usage = run(
                failure -> {},
                new Reply(500, "boom"),
                ok("ledger_mcp_tool_calls_total 3\n"),
                ok(turns("tools", 3)),
                ok(turns("tools", 4)));

        assertThat(usage.ledgerToolCalls()).isNull();
        assertThat(usage.extractionMode()).isEqualTo("tools");
    }

    private static Reply ok(String body) {
        return new Reply(200, body);
    }

    private static String turns(String mode, int count) {
        return "ai_turns_total{mode=\"%s\",outcome=\"acted_on\"} %d\n".formatted(mode, count);
    }

    private static String rpc(String service, String method, int count) {
        return "grpc_server_seconds_count{rpc_service=\"%s\",rpc_method=\"%s\",grpc_status_code=\"OK\"} %d"
                .formatted(service, method, count);
    }

    private TargetUsage connector(Reply before, Reply after) {
        return run(failure -> {}, ok(""), ok(""), before, after);
    }

    private TargetUsage ledger(Reply before, Reply after) {
        return run(failure -> {}, before, after, ok(""), ok(""));
    }

    private TargetUsage run(
            Consumer<EvalException> onFailure,
            Reply ledgerBefore,
            Reply ledgerAfter,
            Reply connectorBefore,
            Reply connectorAfter) {
        String ledgerUrl = serve(ledgerBefore, ledgerAfter);
        String connectorUrl = serve(connectorBefore, connectorAfter);
        TargetMetrics metrics = new TargetMetrics(new Prometheus(HttpClient.newHttpClient()), ledgerUrl, connectorUrl);
        return metrics.open(onFailure).close();
    }

    private String serve(Reply first, Reply second) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AtomicInteger scrapes = new AtomicInteger();
            server.createContext("/", exchange -> {
                Reply reply = scrapes.getAndIncrement() == 0 ? first : second;
                byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(reply.status(), body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            servers.add(server);
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/actuator/prometheus";
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Reply(int status, String body) {}
}
