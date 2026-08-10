package bot.finance.adapter.persistence;

import bot.finance.application.port.ProposalReportRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ProposalReport;
import bot.finance.domain.value.IncomingMessageId;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProposalReportRepositoryAdapter implements ProposalReportRepository {

    private static final String USER_FOREIGN_KEY = "proposal_report_user_id_fkey";

    private final ProposalReportEntityRepository proposalReportEntityRepository;

    public ProposalReportRepositoryAdapter(ProposalReportEntityRepository proposalReportEntityRepository) {
        this.proposalReportEntityRepository = proposalReportEntityRepository;
    }

    @Override
    @Transactional
    public ProposalReport store(ProposalReport report) {
        ProposalReportEntity saved;
        try {
            saved = proposalReportEntityRepository.save(ProposalReportEntity.fromDomain(report));
        } catch (RuntimeException e) {
            throw classify(report, e);
        }
        return saved.toDomain();
    }

    @Override
    public List<ProposalReport> findByIncomingMessageId(long userId, IncomingMessageId id) {
        try {
            return proposalReportEntityRepository
                    .findByUserIdAndIncomingMessageIdOrderByCreatedAtAscIdAsc(userId, id.value())
                    .stream()
                    .map(ProposalReportEntity::toDomain)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to find proposal reports for user " + userId + " and incoming message id " + id.value(), e);
        }
    }

    private static RuntimeException classify(ProposalReport report, RuntimeException e) {
        return switch (ForeignKeyViolations.constraintName(e)) {
            case USER_FOREIGN_KEY -> new EntityNotFoundException("user", "no user stored for id " + report.userId());
            case null, default ->
                new PersistenceFailedException("failed to store proposal report for user " + report.userId(), e);
        };
    }
}
