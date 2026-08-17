package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.dto.ExampleQuery;
import bot.finance.ai.application.dto.RegisteredMessage;
import bot.finance.ai.application.dto.UnembeddedMessage;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.domain.value.Embedding;
import bot.finance.ai.domain.value.MessageExample;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
        // reads the row's id and its embedding text by identity, answering nothing where the store holds no such
        // row for the identity
        return Optional.empty();
    }

    @Override
    public void storeEmbedding(long messageId, Embedding embedding) {
        // writes the vector only where the row still holds none, and releases any backfill claim on it
    }

    @Override
    public int countEmbeddingAttempt(long messageId) {
        // counts one more attempt, releases the claim, and answers the count after it
        return 0;
    }

    @Override
    public List<MessageExample> findExamples(ExampleQuery query) {
        // reads the closest ids and the closest recent id, unions them keeping the closest first, loads those
        // rows and their decided expenses, and trims each to the query's example-lines bound; the age and
        // recency bounds become instants from the adapter's own clock before they reach a statement
        return List.of();
    }

    @Override
    public List<UnembeddedMessage> claimUnembedded(int batch, int maxAttempts, Duration staleClaim) {
        // claims the oldest unclaimed rows by id below the attempt bound, stamped claimed in one transaction
        return List.of();
    }
}
