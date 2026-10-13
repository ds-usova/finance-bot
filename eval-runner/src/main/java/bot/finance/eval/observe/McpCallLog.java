package bot.finance.eval.observe;

import bot.finance.eval.EvalException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the {@code mcp_call} lines the ledger writes to its console, from a file that console is captured into.
 * A read starts at a mark taken before the target was filed, so the file is never read whole.
 */
public final class McpCallLog {

    private static final Pattern LINE =
            Pattern.compile("mcp_call tool=(\\S+) outcome=(\\S+) userId=\\S+ message=(\\S+) arguments=(.*)$");
    private static final JsonMapper JSON = new JsonMapper();

    private final Path file;

    public McpCallLog(Path file) {
        this.file = file;
    }

    public long mark() {
        try {
            return Files.exists(file) ? Files.size(file) : 0;
        } catch (IOException e) {
            throw new EvalException("mcp-calls", "cannot size " + file + ": " + e.getMessage(), null, e);
        }
    }

    public List<McpCall> callsSince(long mark, String incomingMessageId) {
        try (RandomAccessFile reader = new RandomAccessFile(file.toFile(), "r")) {
            long length = reader.length();
            byte[] bytes = new byte[(int) Math.max(0, length - mark)];
            reader.seek(Math.min(mark, length));
            reader.readFully(bytes);
            return parse(new String(bytes, StandardCharsets.UTF_8), incomingMessageId);
        } catch (IOException e) {
            throw new EvalException("mcp-calls", "cannot read " + file + ": " + e.getMessage(), null, e);
        }
    }

    static List<McpCall> parse(String text, String incomingMessageId) {
        List<McpCall> calls = new ArrayList<>();
        for (String line : text.split("\\R")) {
            Matcher matcher = LINE.matcher(line.strip());
            if (matcher.find() && matcher.group(3).equals(incomingMessageId)) {
                calls.add(new McpCall(
                        matcher.group(1),
                        matcher.group(2).equals("ok"),
                        matcher.group(3),
                        JSON.readTree(matcher.group(4))));
            }
        }
        return calls;
    }
}
