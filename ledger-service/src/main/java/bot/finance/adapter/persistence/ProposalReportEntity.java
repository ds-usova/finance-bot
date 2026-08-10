package bot.finance.adapter.persistence;

import bot.finance.domain.model.ProposalReport;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        return ProposalReport.stored(
                id, userId, IncomingMessageId.of(incomingMessageId), conversationId, sentMessageId);
    }

    // stamps created_at and updated_at with the instant the report is stored at
    public static ProposalReportEntity fromDomain(ProposalReport report) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new ProposalReportEntity(
                report.id().orElse(null),
                report.userId(),
                report.incomingMessageId().value(),
                report.conversationId(),
                report.sentMessageId(),
                now,
                now);
    }
}
