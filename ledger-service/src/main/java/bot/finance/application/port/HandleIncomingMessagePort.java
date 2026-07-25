package bot.finance.application.port;

import bot.finance.application.dto.IncomingMessage;

/**
 * Inbound port for a message that arrived from a conversation. The port names the capability, never the
 * transport that delivered the message — every messenger adapter drives this same port.
 */
public interface HandleIncomingMessagePort {

    void handle(IncomingMessage message);

}
