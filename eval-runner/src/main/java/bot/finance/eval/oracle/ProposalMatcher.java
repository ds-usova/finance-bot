package bot.finance.eval.oracle;

import bot.finance.eval.ledger.Proposal;
import bot.finance.eval.oracle.Constraint.PathConstraint;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * The largest one-to-one matching of expected items to proposals that satisfy every constraint of the item,
 * found by augmenting paths rather than by taking the first fit.
 */
final class ProposalMatcher {

    private ProposalMatcher() {}

    /** Expected item index to the id of the proposal it matched; an unmatched item is absent. */
    static Map<Integer, Long> match(JsonNode items, List<Proposal> proposals) {
        boolean[][] fits = fits(items, proposals);
        int[] itemOfProposal = new int[proposals.size()];
        Arrays.fill(itemOfProposal, -1);
        for (int item = 0; item < items.size(); item++) {
            augment(item, fits, itemOfProposal, new boolean[proposals.size()]);
        }

        Map<Integer, Long> pairs = new LinkedHashMap<>();
        for (int proposal = 0; proposal < proposals.size(); proposal++) {
            if (itemOfProposal[proposal] >= 0) {
                pairs.put(itemOfProposal[proposal], proposals.get(proposal).id());
            }
        }
        return pairs;
    }

    private static boolean[][] fits(JsonNode items, List<Proposal> proposals) {
        boolean[][] fits = new boolean[items.size()][proposals.size()];
        for (int item = 0; item < items.size(); item++) {
            List<PathConstraint> constraints = Constraint.flatten(items.get(item));
            for (int proposal = 0; proposal < proposals.size(); proposal++) {
                Proposal candidate = proposals.get(proposal);
                fits[item][proposal] =
                        constraints.stream().allMatch(c -> Constraint.holds(c.constraint(), candidate.field(c.path())));
            }
        }
        return fits;
    }

    private static boolean augment(int item, boolean[][] fits, int[] itemOfProposal, boolean[] visited) {
        for (int proposal = 0; proposal < itemOfProposal.length; proposal++) {
            if (fits[item][proposal] && !visited[proposal]) {
                visited[proposal] = true;
                if (itemOfProposal[proposal] < 0 || augment(itemOfProposal[proposal], fits, itemOfProposal, visited)) {
                    itemOfProposal[proposal] = item;
                    return true;
                }
            }
        }
        return false;
    }
}
