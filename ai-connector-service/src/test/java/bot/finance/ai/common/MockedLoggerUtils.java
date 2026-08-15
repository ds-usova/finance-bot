package bot.finance.ai.common;

import static org.mockito.Mockito.mockingDetails;

import bot.finance.ai.application.port.Logger;
import java.util.Arrays;
import java.util.List;

/**
 * Reads back what a mocked {@link Logger} port received, message and placeholders flattened into one string per
 * line, in call order. The counterpart of {@link LogCapture} for a test that mocks the port instead of booting the
 * real factory.
 */
public final class MockedLoggerUtils {

    private MockedLoggerUtils() {}

    public static List<String> infoLines(Logger log) {
        return linesAt(log, "info");
    }

    public static List<String> warnLines(Logger log) {
        return linesAt(log, "warn");
    }

    public static List<String> linesAt(Logger log, String level) {
        return mockingDetails(log).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals(level))
                .map(invocation -> {
                    Object[] arguments = invocation.getArguments();
                    Object[] placeholders = Arrays.copyOfRange(arguments, 1, arguments.length);
                    return arguments[0] + " " + Arrays.toString(placeholders);
                })
                .toList();
    }
}
