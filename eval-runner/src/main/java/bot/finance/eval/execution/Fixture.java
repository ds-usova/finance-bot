package bot.finance.eval.execution;

import bot.finance.eval.EvalException;
import bot.finance.eval.ledger.LedgerClient;
import java.net.http.HttpClient;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fixture v1 for one attempt: a person signed in for the first time, holding the default catalogue, HUF as the
 * default currency and nothing pending. A new person per attempt is what keeps one case's history from another.
 */
final class Fixture {

    private static final String DEFAULT_CURRENCY = "HUF";

    private Fixture() {}

    static LedgerClient newPerson(HttpClient http, String ledgerUrl, String botToken) {
        LedgerClient ledger = new LedgerClient(http, ledgerUrl);
        ledger.signIn(ThreadLocalRandom.current().nextLong(8_000_000_000L, 9_000_000_000L), botToken);
        ledger.setDefaultCurrency(DEFAULT_CURRENCY);
        long pending = ledger.pendingTotal();
        if (pending != 0) {
            throw new EvalException("setup", "a new person already has " + pending + " pending entries");
        }
        return ledger;
    }
}
