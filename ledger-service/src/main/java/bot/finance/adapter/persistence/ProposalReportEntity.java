package bot.finance.adapter.persistence;

import bot.finance.domain.model.ProposalReport;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("proposal_report")
public record ProposalReportEntity(
        @Id Long id,
        Long userId,
        String incomingMessageId,
        String conversationId,
        String sentMessageId,
        Instant createdAt,
        Instant updatedAt) {

    public ProposalReport toDomain() {
        // maps this row onto the domain ProposalReport
        return null;
    }

    // stamps created_at and updated_at with the instant the report is stored at
    public static ProposalReportEntity fromDomain(ProposalReport report) {
        return null;
    }
}
