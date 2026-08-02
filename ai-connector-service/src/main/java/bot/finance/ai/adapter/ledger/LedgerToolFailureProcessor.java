package bot.finance.ai.adapter.ledger;

import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.stereotype.Component;

@Component
public class LedgerToolFailureProcessor implements ToolExecutionExceptionProcessor {

    @Override
    public String process(ToolExecutionException exception) {
        // TODO: walk exception's cause chain. An McpTransportException anywhere in it rethrows, ending the turn.
        // A cause that is not a RuntimeException at all rethrows too, as the framework's own processor does.
        // Everything else — a tool error result's IllegalStateException, or the protocol's own McpError — returns
        // its message, so the model reads it and can retry.
        throw new UnsupportedOperationException("not yet implemented");
    }

}
