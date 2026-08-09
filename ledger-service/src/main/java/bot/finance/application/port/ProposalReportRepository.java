package bot.finance.application.port;

import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ProposalReport;
import bot.finance.domain.value.IncomingMessageId;
import java.util.List;

public interface ProposalReportRepository {

    /**
     * @throws EntityNotFoundException if the user id names no stored row
     * @throws PersistenceFailedException if the write fails
     */
    ProposalReport store(ProposalReport report);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    List<ProposalReport> findByIncomingMessageId(long userId, IncomingMessageId id);
}
