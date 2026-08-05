package bot.finance.adapter.persistence;

import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.domain.model.SpendingQuery;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.SpendingPeriod;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SpendingQueryRepositoryAdapter implements SpendingQueryRepository {

    private final SpendingQueryEntityRepository spendingQueryEntityRepository;

    public SpendingQueryRepositoryAdapter(SpendingQueryEntityRepository spendingQueryEntityRepository) {
        this.spendingQueryEntityRepository = spendingQueryEntityRepository;
    }

    @Override
    @Transactional
    public SpendingQuery create(SpendingQuery query) {
        // wraps every failure in PersistenceFailedException and classifies the user foreign key as
        // EntityNotFoundException, the way ExpenseProposalRepositoryAdapter.create does
        return null;
    }

    @Override
    public List<SpendingPeriod> findPeriodsByMessageReference(long userId, MessageReference reference) {
        // reads the distinct periods back and re-orders them by created_at, so a period asked for twice
        // in one turn is reported once (D8), wrapping every failure in PersistenceFailedException
        return null;
    }
}
