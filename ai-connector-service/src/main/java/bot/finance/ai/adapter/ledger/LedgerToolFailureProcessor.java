package bot.finance.ai.adapter.ledger;

import io.modelcontextprotocol.spec.McpTransportException;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.stereotype.Component;

@Component
public class LedgerToolFailureProcessor implements ToolExecutionExceptionProcessor {

    @Override
    public String process(ToolExecutionException exception) {
        Throwable cause = exception.getCause();
        if (!(cause instanceof RuntimeException)) {
            throw exception;
        }
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof McpTransportException) {
                throw exception;
            }
        }
        return cause.getMessage();
    }

}
