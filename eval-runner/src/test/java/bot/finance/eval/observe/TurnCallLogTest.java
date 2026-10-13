package bot.finance.eval.observe;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TurnCallLogTest {

    private static final String LINES =
            """
            16:01:02.123 [http-nio-1000-exec-3] DEBUG b.f.adapter.logging.TurnCallLog - turn_call \
            operation=summarize_spending via=mcp outcome=ok userId=42 message=web:17 \
            arguments={"from":"2026-10-05","to":"2026-10-11"}
            16:01:02.456 [http-nio-1000-exec-4] DEBUG b.f.adapter.logging.TurnCallLog - turn_call \
            operation=list_categories via=mcp outcome=rejected userId=42 message=web:17 \
            arguments={"grouping":"Food"}
            16:01:03.000 [http-nio-1000-exec-5] DEBUG b.f.adapter.logging.TurnCallLog - turn_call \
            operation=list_categories via=mcp outcome=ok userId=43 message=web:18 arguments={"grouping":"Dining"}
            16:01:03.100 [main] INFO  b.f.something.Else - unrelated line
            """;

    private static final String MCP_CALL_LINES =
            """
            16:01:02.123 [http-nio-1000-exec-3] DEBUG b.f.adapter.mcp.McpCallLine - mcp_call tool=summarize_spending \
            outcome=ok userId=42 message=web:17 arguments={"from":"2026-10-05","to":"2026-10-11"}
            """;

    private static final String LINE_AFTER_MARK =
            """
            x - turn_call operation=create_expense_proposal via=grpc outcome=ok userId=42 message=web:17 \
            arguments={"grouping":"Food"}
            """;

    @Test
    void readsOnlyTheCallsOfTheNamedMessage() {
        List<TurnCall> calls = TurnCallLog.parse(LINES, "web:17");

        assertThat(calls).extracting(TurnCall::operation).containsExactly("summarize_spending", "list_categories");
        assertThat(calls).extracting(TurnCall::accepted).containsExactly(true, false);
        assertThat(calls.getFirst().arguments().path("from").asString()).isEqualTo("2026-10-05");
    }

    @Test
    void readsNoCallFromMcpCallLines() {
        assertThat(TurnCallLog.parse(MCP_CALL_LINES, "web:17")).isEmpty();
    }

    @Test
    void readsFromTheMarkOnward(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("ledger.log");
        Files.writeString(file, LINES);
        TurnCallLog log = new TurnCallLog(file);
        long mark = log.mark();
        Files.writeString(file, LINES + LINE_AFTER_MARK);

        List<TurnCall> calls = log.callsSince(mark, "web:17");

        assertThat(calls).extracting(TurnCall::operation).containsExactly("create_expense_proposal");
        assertThat(calls).extracting(TurnCall::accepted).containsExactly(true);
        assertThat(calls.getFirst().arguments().size()).isEqualTo(1);
    }
}
