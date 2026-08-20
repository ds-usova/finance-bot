package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.dto.ExampleQuery;
import bot.finance.ai.application.dto.RegisteredMessage;
import bot.finance.ai.application.dto.UnembeddedMessage;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.application.port.RecallMeters;
import bot.finance.ai.domain.value.Embedding;
import bot.finance.ai.domain.value.ExampleExpense;
import bot.finance.ai.domain.value.MessageExample;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class JdbcMessageMemoryAdapter implements MessageMemoryPort {

    private final IncomingMessageEntityRepository messageRepository;
    private final RecordedExpenseEntityRepository expenseRepository;
    private final Clock clock;
    private final RecallMeters recallMeters;

    public JdbcMessageMemoryAdapter(
            IncomingMessageEntityRepository messageRepository,
            RecordedExpenseEntityRepository expenseRepository,
            Clock clock,
            RecallMeters recallMeters) {
        this.messageRepository = messageRepository;
        this.expenseRepository = expenseRepository;
        this.clock = clock;
        this.recallMeters = recallMeters;
    }

    @Override
    public Optional<RegisteredMessage> find(MessageIdentity identity) {
        try {
            Optional<MessageEmbeddingRow> row =
                    messageRepository.findEmbeddingRowByIdentity(identity.userId(), identity.incomingMessageId());
            if (row.isEmpty()) {
                return Optional.empty();
            }

            Optional<Embedding> embedding =
                    Optional.ofNullable(row.get().embedding()).map(VectorText::fromLiteral);

            return Optional.of(new RegisteredMessage(row.get().id(), embedding));
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to find a registered message");
        }
    }

    @Override
    public void storeEmbedding(long messageId, Embedding embedding) {
        try {
            messageRepository.storeEmbedding(messageId, VectorText.toLiteral(embedding));
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to store an embedding");
        }
    }

    @Override
    public int countEmbeddingAttempt(long messageId) {
        try {
            return messageRepository.countEmbeddingAttempt(messageId);
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to count an embedding attempt");
        }
    }

    @Override
    public List<MessageExample> findExamples(ExampleQuery query) {
        try {
            List<Long> orderedIds = neighbourIds(query);

            List<MessageExample> examples = orderedIds.isEmpty() ? List.of() : examplesFor(orderedIds, query);

            recallMeters.recordExamples(examples.size());
            return examples;
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to find examples");
        }
    }

    @Override
    public List<UnembeddedMessage> claimUnembedded(int batch, int maxAttempts, Duration staleClaim) {
        try {
            Instant now = clock.instant();
            Instant staleBefore = now.minus(staleClaim);
            // The claim's ORDER BY sits inside the subquery picking the rows; an UPDATE ... RETURNING does not
            // carry that order out, so the ascending order the caller relies on is imposed here.
            return messageRepository.claimUnembedded(now, maxAttempts, staleBefore, batch).stream()
                    .sorted(Comparator.comparingLong(IncomingMessageEntity::id))
                    .map(entity -> new UnembeddedMessage(entity.id(), entity.text()))
                    .toList();
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to claim unembedded messages");
        }
    }

    private List<MessageExample> examplesFor(List<Long> orderedIds, ExampleQuery query) {
        Map<Long, IncomingMessageEntity> messagesById = messagesById(orderedIds);
        Map<Long, List<RecordedExpenseEntity>> decidedByMessageId =
                decidedExpensesByMessageId(orderedIds, query.exampleLines());

        List<MessageExample> examples = new ArrayList<>();
        for (Long id : orderedIds) {
            IncomingMessageEntity message = messagesById.get(id);
            List<RecordedExpenseEntity> decided = decidedByMessageId.get(id);
            if (message == null || decided == null || decided.isEmpty()) {
                continue;
            }
            examples.add(toExample(message, decided));
        }
        return examples;
    }

    private List<Long> neighbourIds(ExampleQuery query) {
        String embedding = VectorText.toLiteral(query.embedding());
        Instant now = clock.instant();
        Instant cut = now.minus(query.maxAge());
        Instant recentCut = now.minus(query.recentWindow());

        List<ClosestMatchRow> closestRows = messageRepository.findClosestMatches(
                query.userId(), embedding, query.messageId(), query.minSimilarity(), cut, query.examples());
        if (!closestRows.isEmpty()) {
            recallMeters.recordBestSimilarity(closestRows.getFirst().similarity());
        }
        Optional<Long> closestRecentId = messageRepository.findClosestRecentId(
                query.userId(), embedding, query.messageId(), query.minSimilarity(), cut, recentCut);

        Set<Long> ids = new LinkedHashSet<>(
                closestRows.stream().map(ClosestMatchRow::id).toList());
        closestRecentId.ifPresent(ids::add);
        return new ArrayList<>(ids);
    }

    private Map<Long, IncomingMessageEntity> messagesById(List<Long> ids) {
        Map<Long, IncomingMessageEntity> messagesById = new LinkedHashMap<>();
        messageRepository.findAllById(ids).forEach(entity -> messagesById.put(entity.id(), entity));
        return messagesById;
    }

    private Map<Long, List<RecordedExpenseEntity>> decidedExpensesByMessageId(List<Long> ids, int exampleLines) {
        Map<Long, List<RecordedExpenseEntity>> decidedByMessageId = new LinkedHashMap<>();
        for (RecordedExpenseEntity expense : expenseRepository.findDecidedByMessageIds(ids, exampleLines)) {
            decidedByMessageId
                    .computeIfAbsent(expense.messageId(), key -> new ArrayList<>())
                    .add(expense);
        }
        return decidedByMessageId;
    }

    private static MessageExample toExample(IncomingMessageEntity message, List<RecordedExpenseEntity> decided) {
        List<ExampleExpense> expenses =
                decided.stream().map(RecordedExpenseEntity::toExampleExpense).toList();
        return new MessageExample(message.text(), expenses);
    }
}
