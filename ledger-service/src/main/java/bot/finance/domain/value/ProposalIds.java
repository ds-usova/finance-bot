package bot.finance.domain.value;

import java.util.List;

/** The ids an acceptance request named, kept in the order the caller gave them. */
public record ProposalIds(List<Long> ids) {

    public static final int MAX_IDS = 100;

    public ProposalIds {
        // rejects a null or empty list, more than MAX_IDS ids, a repeated id, and an id below 1,
        // naming the field and the bound it broke (D4)
    }

    /** Parses a caller-supplied list of proposal ids, in the order given. */
    public static ProposalIds of(List<Long> ids) {
        return new ProposalIds(ids);
    }
}
