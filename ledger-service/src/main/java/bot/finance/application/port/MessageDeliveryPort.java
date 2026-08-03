package bot.finance.application.port;

import bot.finance.application.dto.ProposalReport;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.MessageDeliveryFailedException;

public interface MessageDeliveryPort {

    /**
     * @throws InvalidIncomingMessageException if the report is absent
     * @throws MessageDeliveryFailedException if delivery fails
     */
    void deliver(ProposalReport report);
}
