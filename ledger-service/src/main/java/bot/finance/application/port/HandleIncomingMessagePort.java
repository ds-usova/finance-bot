package bot.finance.application.port;

import bot.finance.application.dto.IncomingMessage;

public interface HandleIncomingMessagePort {

    void handle(IncomingMessage message);

}
