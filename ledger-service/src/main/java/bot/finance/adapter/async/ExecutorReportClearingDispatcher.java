package bot.finance.adapter.async;

import bot.finance.application.dto.ClearEmptiedReportsCommand;
import bot.finance.application.port.ClearEmptiedReportsPort;
import bot.finance.application.port.ReportClearingDispatchPort;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.stereotype.Component;

@Component
public class ExecutorReportClearingDispatcher implements ReportClearingDispatchPort {

    private final Executor reportClearingExecutor;
    private final ClearEmptiedReportsPort clearEmptiedReportsPort;

    public ExecutorReportClearingDispatcher(
            Executor reportClearingExecutor, ClearEmptiedReportsPort clearEmptiedReportsPort) {
        this.reportClearingExecutor = reportClearingExecutor;
        this.clearEmptiedReportsPort = clearEmptiedReportsPort;
    }

    @Override
    public void dispatch(ClearEmptiedReportsCommand command) {
        try {
            reportClearingExecutor.execute(() -> {
                try {
                    clearEmptiedReportsPort.clear(command);
                } catch (RuntimeException ignored) {
                    // clearing failures inside the submitted task never reach the caller
                }
            });
        } catch (RejectedExecutionException ignored) {
            // a pool with no room is swallowed rather than propagated
        }
    }
}
