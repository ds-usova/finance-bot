package bot.finance.adapter.persistence;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface ProposalReportEntityRepository extends CrudRepository<ProposalReportEntity, Long> {

    List<ProposalReportEntity> findByUserIdAndIncomingMessageIdOrderByCreatedAtAscIdAsc(
            Long userId, String incomingMessageId);
}
