package bot.finance.adapter.async;

import bot.finance.application.dto.ClearEmptiedReportsCommand;
import bot.finance.application.port.ClearEmptiedReportsPort;
import bot.finance.application.port.ReportClearingDispatchPort;
import java.util.concurrent.Executor;
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
        // submits the clearing to the executor, off the request thread; a RejectedExecutionException from a
        // pool with no room is swallowed rather than propagated, and a failure the clearing itself throws
        // inside the submitted task never reaches the caller either
    }
}
