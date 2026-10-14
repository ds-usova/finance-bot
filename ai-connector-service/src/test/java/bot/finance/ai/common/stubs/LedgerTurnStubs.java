package bot.finance.ai.common.stubs;

import bot.finance.ai.common.containers.LedgerTurnServer;
import bot.finance.ai.common.containers.LedgerTurnServer.Outcome;
import bot.finance.ai.common.containers.LedgerTurnServer.RecordedCall;
import io.grpc.Status;
import java.util.List;

/**
 * Scripts the fake ledger turn service one outcome per call, in order, and reads back the calls it recorded. The
 * script is spent once every outcome has answered a call; a call after that is stored.
 */
public final class LedgerTurnStubs {

    private LedgerTurnStubs() {}

    public static void scriptStored() {
        LedgerTurnServer.script(new Outcome.Stored());
    }

    /** Scripts a refusal with {@code status}; {@code INVALID_ARGUMENT} refuses one call, any other ends the turn. */
    public static void scriptRefused(Status status) {
        LedgerTurnServer.script(new Outcome.Refused(status));
    }

    public static void scriptNeverAnswering() {
        LedgerTurnServer.script(new Outcome.NeverAnswering());
    }

    public static List<RecordedCall> recordedCalls() {
        return LedgerTurnServer.recordedCalls();
    }

    public static void reset() {
        LedgerTurnServer.reset();
    }
}
