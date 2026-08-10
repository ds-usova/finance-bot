package bot.finance.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Enforces what [Code Style] says about comments citing a plan step or a design decision by number. A source
 * scan rather than an ArchUnit rule, because a comment is not in the bytecode ArchUnit reads.
 *
 * <p>Only comment lines are searched: a plan's own item ids are how a {@code @Disabled} names the step that owes
 * a test its rework, which [Testing Conventions] requires, and that is an annotation rather than a comment.
 */
class CommentConventionsTest {

    private static final List<Path> SOURCE_ROOTS = List.of(Path.of("src/main/java"), Path.of("src/test/java"));

    private static final Pattern CITATION =
            Pattern.compile("\\b(?:ST|RU|RI|RS|GU|GI|GS)\\d{2}\\b|\\b[DQPB]\\d{1,2}\\b");

    // Carried as data rather than as a comment, which the scan would read as a citation of its own, and shown
    // in the failure so it says what shape it refused.
    private static final String EXAMPLES = "ST01, RU03, GS01, D34, Q1, P01, B2";

    private static final Pattern COMMENT_LINE = Pattern.compile("(^|\\s)(//|/\\*|\\*)");

    @Nested
    @DisplayName("a comment citing a plan step or a design decision")
    class PlanCitations {

        @Test
        @DisplayName("when every source line is read - then no comment names a plan step or a decision by number")
        void whenEverySourceLineIsRead_thenNoCommentNamesAPlanStepOrDecisionByNumber() {
            List<Path> sources = sourceFiles();

            // The scan resolves its roots against the working directory, so a run from elsewhere would find
            // nothing and pass while proving nothing.
            assertThat(sources).as("source files found under %s", SOURCE_ROOTS).isNotEmpty();

            // Named in full rather than counted, so a failure says which comment to rewrite.
            assertThat(sources.stream()
                            .flatMap(CommentConventionsTest::citationsIn)
                            .toList())
                    .as("comments citing a plan step or a decision by number, such as %s", EXAMPLES)
                    .isEmpty();
        }
    }

    private static List<Path> sourceFiles() {
        return SOURCE_ROOTS.stream()
                .filter(Files::isDirectory)
                .flatMap(CommentConventionsTest::javaFilesUnder)
                .toList();
    }

    private static Stream<Path> javaFilesUnder(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + root, e);
        }
    }

    private static Stream<String> citationsIn(Path file) {
        return lines(file).stream()
                .filter(line -> COMMENT_LINE.matcher(line).find())
                .filter(line -> CITATION.matcher(line).find())
                .map(line -> file + ": " + line.strip());
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file, e);
        }
    }
}
