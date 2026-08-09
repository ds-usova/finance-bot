package bot.finance.application.port;

import bot.finance.application.dto.ReportLocation;
import bot.finance.application.dto.ResolutionAcknowledgement;
import bot.finance.application.dto.TurnReport;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import java.util.Optional;

public interface MessageDeliveryPort {

    /**
     * @throws InvalidIncomingMessageException if the report is absent
     * @throws MessageDeliveryFailedException if delivery fails
     */
    Optional<ReportLocation> deliver(TurnReport report);

    /**
     * @throws InvalidIncomingMessageException if the acknowledgement is absent
     * @throws MessageDeliveryFailedException if delivery fails
     */
    void acknowledge(ResolutionAcknowledgement ack);

    /**
     * @throws MessageDeliveryFailedException if the edit fails
     */
    void clearButtons(ReportLocation location);
}
