package bot.finance.application.port;

import bot.finance.application.dto.ResolutionAcknowledgement;
import bot.finance.application.dto.TurnReport;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.MessageDeliveryFailedException;

public interface MessageDeliveryPort {

    /**
     * @throws InvalidIncomingMessageException if the report is absent
     * @throws MessageDeliveryFailedException if delivery fails
     */
    void deliver(TurnReport report);

    /**
     * @throws InvalidIncomingMessageException if the acknowledgement is absent
     * @throws MessageDeliveryFailedException if delivery fails
     */
    void acknowledge(ResolutionAcknowledgement ack);
}
