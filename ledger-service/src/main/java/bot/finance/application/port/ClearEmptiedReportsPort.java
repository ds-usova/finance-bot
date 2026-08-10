package bot.finance.application.port;

import bot.finance.application.dto.ClearEmptiedReportsCommand;

public interface ClearEmptiedReportsPort {

    void clear(ClearEmptiedReportsCommand command);
}
