package bot.finance.domain.model;

import bot.finance.domain.value.IncomingMessageId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

public class IncomingTurn extends Entity {

    private final long userId;
    private final IncomingMessageId reference;
    private final String text;
    private final String conversationId;
    private final String inboundMessageId;
    private final Instant keptAt;
    private final Optional<Instant> lastAttemptAt;
    private final Optional<Instant> handedOverAt;

    private IncomingTurn(
            Long id,
            long userId,
            String conversationId,
            String inboundMessageId,
            String text,
            Instant keptAt,
            Optional<Instant> lastAttemptAt,
            Optional<Instant> handedOverAt) {
        super(id);
        // TODO: assert a positive id and user id, a present conversation id, inbound message id, text and kept-at
        // instant and present attempt and hand-over optionals, throwing InvalidIncomingTurnException
        this.userId = userId;
        this.reference = IncomingMessageId.of(conversationId, inboundMessageId);
        this.text = text;
        this.conversationId = conversationId;
        this.inboundMessageId = inboundMessageId;
        this.keptAt = keptAt;
        this.lastAttemptAt = lastAttemptAt;
        this.handedOverAt = handedOverAt;
    }

    public static IncomingTurn newTurn(
            long userId, String conversationId, String inboundMessageId, String text, Instant keptAt) {
        return new IncomingTurn(
                null, userId, conversationId, inboundMessageId, text, keptAt, Optional.empty(), Optional.empty());
    }

    public static IncomingTurn stored(
            long id,
            long userId,
            String conversationId,
            String inboundMessageId,
            String text,
            Instant keptAt,
            Optional<Instant> lastAttemptAt,
            Optional<Instant> handedOverAt) {
        return new IncomingTurn(
                id, userId, conversationId, inboundMessageId, text, keptAt, lastAttemptAt, handedOverAt);
    }

    public long userId() {
        return userId;
    }

    public IncomingMessageId reference() {
        return reference;
    }

    public String text() {
        return text;
    }

    public String conversationId() {
        return conversationId;
    }

    public String inboundMessageId() {
        return inboundMessageId;
    }

    public Instant keptAt() {
        return keptAt;
    }

    public Optional<Instant> lastAttemptAt() {
        return lastAttemptAt;
    }

    public Optional<Instant> handedOverAt() {
        return handedOverAt;
    }

    public LocalDate keptOn(ZoneId zone) {
        // stub-intent: the day the turn was kept, read in the zone given
        return null;
    }
}
