package bot.finance.ai.adapter.ledger;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerToolFailureProcessorTest {

    private final LedgerToolFailureProcessor processor = new LedgerToolFailureProcessor();

    private static ToolDefinition toolDefinition() {
        return ToolDefinition.builder()
                .name("record_expense")
                .description("Records an expense in the ledger")
                .inputSchema("{}")
                .build();
    }

    @Nested
    @DisplayName("processing a ToolExecutionException")
    class Process {

        @Test
        @DisplayName("when the direct cause is an IllegalStateException carrying the ledger's refusal text - "
                + "then returns that text")
        void whenDirectCauseIsIllegalStateExceptionCarryingRefusalText_thenReturnsThatText() {
            IllegalStateException refusal = new IllegalStateException("duplicate expense already recorded");
            ToolExecutionException exception = new ToolExecutionException(toolDefinition(), refusal);

            String result = processor.process(exception);

            assertThat(result).isEqualTo("duplicate expense already recorded");
        }

        @Test
        @DisplayName("when the direct cause is an McpError - then returns its message rather than rethrowing")
        void whenDirectCauseIsMcpError_thenReturnsItsMessageRatherThanRethrowing() {
            McpError mcpError = new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(
                    McpSchema.ErrorCodes.INVALID_PARAMS, "amount must be positive", null));
            ToolExecutionException exception = new ToolExecutionException(toolDefinition(), mcpError);

            String result = processor.process(exception);

            assertThat(result).isEqualTo("amount must be positive");
        }

        @Test
        @DisplayName("when the direct cause is an McpTransportException - then rethrows, ending the turn")
        void whenDirectCauseIsMcpTransportException_thenRethrowsEndingTheTurn() {
            McpTransportException transportException = new McpTransportException("connection reset");
            ToolExecutionException exception = new ToolExecutionException(toolDefinition(), transportException);

            assertThatThrownBy(() -> processor.process(exception)).isSameAs(exception);
        }

        @Test
        @DisplayName("when the cause is a plain RuntimeException wrapping an McpTransportException - then "
                + "rethrows, because the chain is walked rather than the direct cause matched")
        void whenCauseIsRuntimeExceptionWrappingMcpTransportException_thenRethrowsBecauseChainIsWalked() {
            McpTransportException transportException = new McpTransportException("initialization failed");
            RuntimeException wrapper = new RuntimeException("lifecycle initialization failed", transportException);
            ToolExecutionException exception = new ToolExecutionException(toolDefinition(), wrapper);

            assertThatThrownBy(() -> processor.process(exception)).isSameAs(exception);
        }

        @Test
        @DisplayName("when the cause is a checked Exception - then rethrows, as the framework's own processor does")
        void whenCauseIsCheckedException_thenRethrowsAsFrameworksOwnProcessorDoes() {
            Exception checked = new Exception("checked failure");
            ToolExecutionException exception = new ToolExecutionException(toolDefinition(), checked);

            assertThatThrownBy(() -> processor.process(exception)).isSameAs(exception);
        }

    }

}
