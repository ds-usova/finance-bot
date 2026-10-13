package bot.finance.adapter.async;

import bot.finance.application.dto.ClearEmptiedReportsCommand;
import bot.finance.application.port.ClearEmptiedReportsPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.ReportClearingDispatchPort;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.stereotype.Component;

@Component
public class ExecutorReportClearingDispatcher implements ReportClearingDispatchPort {

    private final Executor reportClearingExecutor;
    private final ClearEmptiedReportsPort clearEmptiedReportsPort;
    private final Logger logger;

    public ExecutorReportClearingDispatcher(
            Executor reportClearingExecutor,
            ClearEmptiedReportsPort clearEmptiedReportsPort,
            LoggerFactory loggerFactory) {
        this.reportClearingExecutor = reportClearingExecutor;
        this.clearEmptiedReportsPort = clearEmptiedReportsPort;
        this.logger = loggerFactory.getLogger(ExecutorReportClearingDispatcher.class);
    }

    @Override
    public void dispatch(ClearEmptiedReportsCommand command) {
        try {
            reportClearingExecutor.execute(() -> {
                try {
                    clearEmptiedReportsPort.clear(command);
                } catch (RuntimeException e) {
                    logger.warn("clearing the emptied reports of user {} failed", command.userId(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            logger.warn("dropped clearing the emptied reports of user {}: the pool has no room", command.userId());
        }
    }
}
