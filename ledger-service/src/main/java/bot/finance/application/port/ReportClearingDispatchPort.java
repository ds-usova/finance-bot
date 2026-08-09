package bot.finance.application.port;

import bot.finance.application.dto.ClearEmptiedReportsCommand;

public interface ReportClearingDispatchPort {

    void dispatch(ClearEmptiedReportsCommand command);
}
