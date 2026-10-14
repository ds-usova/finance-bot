package bot.finance.ai.system;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.AiConnectorServiceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Starts the application itself, the way {@code main()} does, with the test profile and ports the operating system
 * picks. A start that succeeds would leave a running context behind, so the scenarios only ever expect a failure.
 */
class ExtractionModeStartupSystemTest {

    private static SpringApplicationBuilder application(String... properties) {
        return new SpringApplicationBuilder(AiConnectorServiceApplication.class)
                .profiles("test")
                .properties("spring.grpc.server.port=0", "classifier.api-key=test-key", "classifier.model=test-model")
                .properties(properties);
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when EXTRACTION_MODE is rules - then the application fails to start naming EXTRACTION_MODE")
        void whenExtractionModeIsRules_thenApplicationFailsToStartNamingExtractionMode() {
            assertThatThrownBy(() -> application("extraction.mode=rules").run())
                    .hasStackTraceContaining("EXTRACTION_MODE");
        }

        @Test
        @DisplayName("when EXTRACTION_MODE is classifier and there is no LEDGER_TURN_TARGET - then the application "
                + "fails to start naming LEDGER_TURN_TARGET")
        void whenClassifierModeHasNoLedgerTurnTarget_thenApplicationFailsToStartNamingLedgerTurnTarget() {
            assertThatThrownBy(() -> application("extraction.mode=classifier", "ledger.turn.target=")
                            .run())
                    .hasStackTraceContaining("LEDGER_TURN_TARGET");
        }
    }
}
