package bot.finance.eval.observe;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpCallLogTest {

    private static final String LINES =
            """
            16:01:02.123 [http-nio-1000-exec-3] DEBUG b.f.adapter.mcp.McpCallLine - mcp_call tool=summarize_spending \
            outcome=ok userId=42 message=web:17 arguments={"from":"2026-10-05","to":"2026-10-11"}
            16:01:02.456 [http-nio-1000-exec-4] DEBUG b.f.adapter.mcp.McpCallLine - mcp_call tool=list_categories \
            outcome=rejected userId=42 message=web:17 arguments={"grouping":"Food"}
            16:01:03.000 [http-nio-1000-exec-5] DEBUG b.f.adapter.mcp.McpCallLine - mcp_call tool=list_categories \
            outcome=ok userId=43 message=web:18 arguments={"grouping":"Dining"}
            16:01:03.100 [main] INFO  b.f.something.Else - unrelated line
            """;

    @Test
    void readsOnlyTheCallsOfTheNamedMessage() {
        List<McpCall> calls = McpCallLog.parse(LINES, "web:17");

        assertThat(calls).extracting(McpCall::tool).containsExactly("summarize_spending", "list_categories");
        assertThat(calls).extracting(McpCall::accepted).containsExactly(true, false);
        assertThat(calls.getFirst().arguments().path("from").asString()).isEqualTo("2026-10-05");
    }

    @Test
    void readsFromTheMarkOnward(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("ledger.log");
        Files.writeString(file, LINES);
        McpCallLog log = new McpCallLog(file);
        long mark = log.mark();
        Files.writeString(
                file,
                LINES + "x - mcp_call tool=create_expense_proposal outcome=ok userId=42 message=web:17 arguments={}\n");

        assertThat(log.callsSince(mark, "web:17")).extracting(McpCall::tool).containsExactly("create_expense_proposal");
    }
}
