package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.dto.ExampleQuery;
import bot.finance.ai.application.dto.RegisteredMessage;
import bot.finance.ai.application.dto.UnembeddedMessage;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.Embedding;
import bot.finance.ai.domain.value.ExampleExpense;
import bot.finance.ai.domain.value.ExampleOutcome;
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

    public JdbcMessageMemoryAdapter(
            IncomingMessageEntityRepository messageRepository,
            RecordedExpenseEntityRepository expenseRepository,
            Clock clock) {
        this.messageRepository = messageRepository;
        this.expenseRepository = expenseRepository;
        this.clock = clock;
    }

    @Override
    public Optional<RegisteredMessage> find(MessageIdentity identity) {
        try {
            Optional<Long> id = messageRepository.findIdByIdentity(identity.userId(), identity.incomingMessageId());
            if (id.isEmpty()) {
                return Optional.empty();
            }
            Optional<Embedding> embedding =
                    messageRepository.findEmbeddingText(id.get()).map(VectorText::fromLiteral);
            return Optional.of(new RegisteredMessage(id.get(), embedding));
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
            String embedding = VectorText.toLiteral(query.embedding());
            Instant cut = clock.instant().minus(query.maxAge());
            Instant recentCut = clock.instant().minus(query.recentWindow());

            List<Long> closestIds = messageRepository.findClosestIds(
                    query.userId(), embedding, query.messageId(), query.minSimilarity(), cut, query.examples());
            Optional<Long> closestRecentId = messageRepository.findClosestRecentId(
                    query.userId(), embedding, query.messageId(), query.minSimilarity(), cut, recentCut);

            Set<Long> ids = new LinkedHashSet<>(closestIds);
            closestRecentId.ifPresent(ids::add);
            if (ids.isEmpty()) {
                return List.of();
            }

            List<Long> orderedIds = new ArrayList<>(ids);
            Map<Long, IncomingMessageEntity> messagesById = new LinkedHashMap<>();
            messageRepository.findAllById(orderedIds).forEach(entity -> messagesById.put(entity.id(), entity));

            Map<Long, List<RecordedExpenseEntity>> expensesByMessageId = new LinkedHashMap<>();
            for (RecordedExpenseEntity expense : expenseRepository.findDecidedByMessageIds(orderedIds)) {
                expensesByMessageId
                        .computeIfAbsent(expense.messageId(), key -> new ArrayList<>())
                        .add(expense);
            }

            List<MessageExample> examples = new ArrayList<>();
            for (Long id : orderedIds) {
                IncomingMessageEntity message = messagesById.get(id);
                List<RecordedExpenseEntity> decided = expensesByMessageId.get(id);
                if (message == null || decided == null || decided.isEmpty()) {
                    continue;
                }
                List<ExampleExpense> expenses = decided.stream()
                        .limit(query.exampleLines())
                        .map(this::toExampleExpense)
                        .toList();
                examples.add(new MessageExample(message.text(), expenses));
            }
            return examples;
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to find examples");
        }
    }

    private ExampleExpense toExampleExpense(RecordedExpenseEntity expense) {
        CurrencyCode currency = CurrencyCode.of(expense.currencyCode());
        return new ExampleExpense(
                expense.description(),
                currency.toDecimal(expense.amountMinorUnits()),
                currency,
                Optional.ofNullable(expense.categoryName()),
                Optional.ofNullable(expense.groupingName()),
                ExampleOutcome.valueOf(expense.status()));
    }

    @Override
    public List<UnembeddedMessage> claimUnembedded(int batch, int maxAttempts, Duration staleClaim) {
        try {
            Instant now = clock.instant();
            Instant staleBefore = now.minus(staleClaim);
            return messageRepository.claimUnembedded(now, maxAttempts, staleBefore, batch).stream()
                    .sorted(Comparator.comparingLong(IncomingMessageEntity::id))
                    .map(entity -> new UnembeddedMessage(entity.id(), entity.text()))
                    .toList();
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to claim unembedded messages");
        }
    }
}
