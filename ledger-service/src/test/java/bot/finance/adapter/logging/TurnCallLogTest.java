package bot.finance.adapter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.LogCapture;
import bot.finance.common.fixtures.McpTokens;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

class TurnCallLogTest {

    private LogCapture logCapture;

    @BeforeEach
    void attachLogCapture() {
        logCapture = LogCapture.attachedTo(TurnCallLog.class);
    }

    @AfterEach
    void cleanUp() {
        logCapture.close();
        SecurityContextHolder.clearContext();
    }

    private static TurnCallLog turnCallLog(boolean enabled) {
        return new TurnCallLog(new TurnCallLogProperties(enabled), JsonMapper.builder().build(), new Slf4jLoggerFactory());
    }

    private static void authenticateUser42OnMessage17() {
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .subject("42")
                .claim(McpTokens.INCOMING_MESSAGE_ID_CLAIM, "web:17")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Nested
    @DisplayName("writing a turn call line")
    class Write {

        @Test
        @DisplayName("when the line is enabled and a verified caller made an accepted MCP call - then one full line "
                + "is logged")
        void whenEnabledAndCallerVerifiedAndMcpCallAccepted_thenOneFullLineIsLogged() {
            authenticateUser42OnMessage17();

            turnCallLog(true)
                    .write(
                            "create_expense_proposal",
                            TurnCallLog.Via.MCP,
                            true,
                            TurnCallLog.arguments("grouping", "Dining", "amount", "4.50"));

            assertThat(logCapture.messages())
                    .containsExactly("""
                            turn_call operation=create_expense_proposal via=mcp outcome=ok userId=42 message=web:17 \
                            arguments={"grouping":"Dining","amount":"4.50"}""");
        }

        @Test
        @DisplayName("when a gRPC call was rejected - then the line reads via=grpc and outcome=rejected")
        void whenGrpcCallRejected_thenLineReadsViaGrpcAndOutcomeRejected() {
            authenticateUser42OnMessage17();

            turnCallLog(true)
                    .write("summarize_spending", TurnCallLog.Via.GRPC, false, TurnCallLog.arguments("from", "2026-10-12"));

            assertThat(logCapture.messages()).hasSize(1);
            assertThat(logCapture.messages().get(0))
                    .contains("operation=summarize_spending")
                    .contains("via=grpc")
                    .contains("outcome=rejected");
        }

        @Test
        @DisplayName("when no caller is authenticated - then the line reads userId=none message=none")
        void whenNoCallerIsAuthenticated_thenLineReadsUserIdNoneMessageNone() {
            turnCallLog(true).write("create_expense_proposal", TurnCallLog.Via.MCP, true, TurnCallLog.arguments());

            assertThat(logCapture.messages()).hasSize(1);
            assertThat(logCapture.messages().get(0)).contains("userId=none message=none");
        }

        @Test
        @DisplayName("when the line is disabled - then nothing is logged")
        void whenLineIsDisabled_thenNothingIsLogged() {
            authenticateUser42OnMessage17();

            turnCallLog(false)
                    .write("create_expense_proposal", TurnCallLog.Via.MCP, true, TurnCallLog.arguments("amount", "4.50"));

            assertThat(logCapture.messages()).isEmpty();
        }
    }
}
