package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidExpenseAcceptanceException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The ids an acceptance request named, kept in the order the caller gave them. */
public record ProposalIds(List<Long> ids) {

    public static final int MAX_IDS = 100;

    public ProposalIds {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidExpenseAcceptanceException("ids must not be empty");
        }
        if (ids.size() > MAX_IDS) {
            throw new InvalidExpenseAcceptanceException("ids must contain at most " + MAX_IDS + " ids");
        }
        Set<Long> distinct = new HashSet<>(ids);
        if (distinct.size() != ids.size()) {
            throw new InvalidExpenseAcceptanceException("ids must not contain a duplicate id");
        }
        for (Long id : ids) {
            if (id == null || id < 1) {
                throw new InvalidExpenseAcceptanceException("id must be at least 1");
            }
        }
    }

    /** Parses a caller-supplied list of proposal ids, in the order given. */
    public static ProposalIds of(List<Long> ids) {
        return new ProposalIds(ids);
    }
}
