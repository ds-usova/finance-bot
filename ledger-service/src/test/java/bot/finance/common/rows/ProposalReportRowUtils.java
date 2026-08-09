package bot.finance.common.rows;

import bot.finance.adapter.persistence.ProposalReportEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

public class ProposalReportRowUtils {

    private ProposalReportRowUtils() {}

    public static List<ProposalReportEntity> proposalReportRowsFor(
            JdbcAggregateTemplate jdbcAggregateTemplate, long userId) {
        return jdbcAggregateTemplate.findAll(ProposalReportEntity.class).stream()
                .filter(row -> row.userId() == userId)
                .toList();
    }

    public static ProposalReportEntity storedReport(
            JdbcAggregateTemplate jdbcAggregateTemplate,
            long userId,
            String incomingMessageId,
            String conversationId,
            String sentMessageId,
            Instant createdAt) {
        return jdbcAggregateTemplate.insert(new ProposalReportEntity(
                null, userId, incomingMessageId, conversationId, sentMessageId, createdAt, createdAt));
    }
}
