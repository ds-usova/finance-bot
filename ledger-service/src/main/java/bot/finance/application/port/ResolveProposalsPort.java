package bot.finance.application.port;

import bot.finance.application.dto.ResolveProposalsCommand;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import bot.finance.domain.exception.PersistenceFailedException;

public interface ResolveProposalsPort {

    /**
     * @throws InvalidIncomingMessageException if the command is absent
     * @throws PersistenceFailedException if the resolution fails
     * @throws MessageDeliveryFailedException if the acknowledgement cannot be delivered
     */
    void resolve(ResolveProposalsCommand command);
}
