package bot.finance.adapter.persistence;

import bot.finance.domain.model.IncomingTurn;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("incoming_turn")
public record IncomingTurnEntity(
        @Id Long id,
        Long userId,
        String incomingMessageId,
        String text,
        String conversationId,
        String inboundMessageId,
        Instant keptAt,
        Instant lastAttemptAt,
        Instant handedOverAt) {

    public IncomingTurn toDomain() {
        return IncomingTurn.stored(
                id,
                userId,
                conversationId,
                inboundMessageId,
                text,
                keptAt,
                Optional.ofNullable(lastAttemptAt),
                Optional.ofNullable(handedOverAt));
    }

    public static IncomingTurnEntity fromDomain(IncomingTurn turn) {
        return new IncomingTurnEntity(
                turn.id().orElse(null),
                turn.userId(),
                turn.reference().value(),
                turn.text(),
                turn.conversationId(),
                turn.inboundMessageId(),
                turn.keptAt().truncatedTo(ChronoUnit.MICROS),
                turn.lastAttemptAt()
                        .map(at -> at.truncatedTo(ChronoUnit.MICROS))
                        .orElse(null),
                turn.handedOverAt().map(at -> at.truncatedTo(ChronoUnit.MICROS)).orElse(null));
    }
}
