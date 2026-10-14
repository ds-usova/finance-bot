package bot.finance.ai.system;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.AiConnectorServiceApplication;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Starts the application itself, the way {@code main()} does, with the test profile and ports the operating system
 * picks. A start that succeeds would leave a running context behind, so the scenarios only ever expect a failure.
 */
class ExtractionModeStartupSystemTest {

    private static final String[] BASE_ARGUMENTS = {
        "--server.port=0",
        "--spring.grpc.server.port=0",
        "--classifier.api-key=test-key",
        "--classifier.model=test-model"
    };

    private static SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(AiConnectorServiceApplication.class).profiles("test");
    }

    private static String[] arguments(String... extra) {
        String[] all = Arrays.copyOf(BASE_ARGUMENTS, BASE_ARGUMENTS.length + extra.length);
        System.arraycopy(extra, 0, all, BASE_ARGUMENTS.length, extra.length);
        return all;
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when EXTRACTION_MODE is rules - then the application fails to start naming EXTRACTION_MODE")
        void whenExtractionModeIsRules_thenApplicationFailsToStartNamingExtractionMode() {
            assertThatThrownBy(() -> application().run(arguments("--extraction.mode=rules")))
                    .hasStackTraceContaining("EXTRACTION_MODE");
        }

        @Test
        @DisplayName("when classifier mode has no LEDGER_TURN_TARGET - then startup fails naming LEDGER_TURN_TARGET")
        void whenClassifierModeHasNoLedgerTurnTarget_thenApplicationFailsToStartNamingLedgerTurnTarget() {
            assertThatThrownBy(
                            () -> application().run(arguments("--extraction.mode=classifier", "--ledger.turn.target=")))
                    .hasStackTraceContaining("LEDGER_TURN_TARGET");
        }
    }
}
