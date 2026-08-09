package bot.finance.adapter.persistence;

import bot.finance.application.port.ProposalReportRepository;
import bot.finance.domain.model.ProposalReport;
import bot.finance.domain.value.IncomingMessageId;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProposalReportRepositoryAdapter implements ProposalReportRepository {

    private final ProposalReportEntityRepository proposalReportEntityRepository;

    public ProposalReportRepositoryAdapter(ProposalReportEntityRepository proposalReportEntityRepository) {
        this.proposalReportEntityRepository = proposalReportEntityRepository;
    }

    @Override
    @Transactional
    public ProposalReport store(ProposalReport report) {
        // inserts one row per call, never updates an existing one, classifying a foreign-key violation on
        // user_id as EntityNotFoundException (D53)
        return null;
    }

    @Override
    public List<ProposalReport> findByIncomingMessageId(long userId, IncomingMessageId id) {
        // answers every row recorded for this user and this message, in the order they were recorded (D53)
        return null;
    }
}
