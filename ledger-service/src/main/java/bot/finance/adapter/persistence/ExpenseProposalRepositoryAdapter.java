package bot.finance.adapter.persistence;

import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExpenseProposalRepositoryAdapter implements ExpenseProposalRepository {

    // Postgres SQLState for a foreign key violation.
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CATEGORY_FOREIGN_KEY = "expense_proposal_category_id_fkey";
    private static final String USER_FOREIGN_KEY = "expense_proposal_user_id_fkey";
    private static final Pattern CONSTRAINT_NAME_PATTERN = Pattern.compile("constraint \"([^\"]+)\"");

    private final ExpenseProposalEntityRepository expenseProposalEntityRepository;

    public ExpenseProposalRepositoryAdapter(ExpenseProposalEntityRepository expenseProposalEntityRepository) {
        this.expenseProposalEntityRepository = expenseProposalEntityRepository;
    }

    @Override
    @Transactional
    public ExpenseProposal create(ExpenseProposal proposal) {
        ColumnLimits.validateExpenseProposalText(
                proposal.description(), proposal.merchant().orElse(null));

        ExpenseProposalEntity saved;
        try {
            saved = expenseProposalEntityRepository.save(truncatedToMicros(proposal));
        } catch (RuntimeException e) {
            throw classify(proposal, e);
        }
        return saved.toDomain();
    }

    private static RuntimeException classify(ExpenseProposal proposal, RuntimeException e) {
        return switch (foreignKeyConstraintName(e)) {
            case CATEGORY_FOREIGN_KEY ->
                new EntityNotFoundException("category", "no category stored for id " + proposal.categoryId());
            case USER_FOREIGN_KEY -> new EntityNotFoundException("user", "no user stored for id " + proposal.userId());
            case null, default ->
                new PersistenceFailedException("failed to store expense proposal for user " + proposal.userId(), e);
        };
    }

    // The constraint name is not exposed as a structured field anywhere in the exception chain -
    // Postgres reports it only inside the SQLException's message text.
    private static String foreignKeyConstraintName(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && FOREIGN_KEY_VIOLATION.equals(sqlException.getSQLState())) {
                return constraintNameFromMessage(sqlException.getMessage());
            }
        }
        return null;
    }

    private static String constraintNameFromMessage(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = CONSTRAINT_NAME_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    // The column's microsecond precision does not round-trip nanosecond-precision instants: the
    // driver rounds rather than truncates. Truncating to microseconds before writing removes the
    // sub-microsecond remainder so the stored value is exact.
    private static ExpenseProposalEntity truncatedToMicros(ExpenseProposal proposal) {
        ExpenseProposalEntity mapped = ExpenseProposalEntity.fromDomain(proposal);
        return new ExpenseProposalEntity(
                mapped.id(),
                mapped.userId(),
                mapped.categoryId(),
                mapped.description(),
                mapped.merchant(),
                mapped.amountMinorUnits(),
                mapped.currencyCode(),
                mapped.createdAt().truncatedTo(ChronoUnit.MICROS),
                mapped.updatedAt().truncatedTo(ChronoUnit.MICROS));
    }
}
