package bot.finance.eval.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads two run files and writes their comparison to {@code compare.md} beside the second. */
public final class CompareCommand {

    private static final Logger LOG = LoggerFactory.getLogger(CompareCommand.class);
    private static final JsonMapper JSON = new JsonMapper();

    private CompareCommand() {}

    public static void run(Path before, Path after) {
        String table = Comparison.render(read(before), read(after));
        Path out = after.toAbsolutePath().resolveSibling("compare.md");
        try {
            Files.writeString(out, table);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + out, e);
        }
        LOG.info("wrote {}\n{}", out, table);
    }

    private static JsonNode read(Path file) {
        try {
            return JSON.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}
