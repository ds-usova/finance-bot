package bot.finance.ai.common.stubs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.ai.common.containers.LedgerTurnServer;
import bot.finance.ai.common.containers.LedgerTurnServer.Outcome;
import bot.finance.ai.common.containers.LedgerTurnServer.RecordedCall;
import bot.finance.ledger.adapter.grpc.v1.EndTurnRequest;
import bot.finance.ledger.adapter.grpc.v1.TurnEnd;
import io.grpc.Status;
import java.time.Duration;
import java.util.List;

/**
 * Scripts the fake ledger turn service and the fake turn end service one outcome per call, in order, and reads
 * back the calls each recorded. A script is spent once every outcome has answered a call; a call after that is
 * stored, or taken for an end.
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

    public static void scriptEndTaken() {
        LedgerTurnServer.scriptEnd(new Outcome.Stored());
    }

    public static void scriptEndRefused(Status status) {
        LedgerTurnServer.scriptEnd(new Outcome.Refused(status));
    }

    public static void scriptEndNeverAnswering() {
        LedgerTurnServer.scriptEnd(new Outcome.NeverAnswering());
    }

    public static List<RecordedCall> recordedEnds() {
        return LedgerTurnServer.recordedEnds();
    }

    public static List<RecordedCall> awaitEnds(int count) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> LedgerTurnServer.recordedEnds().size() >= count);
        return LedgerTurnServer.recordedEnds();
    }

    /** Waits for the one end a turn sends, asserts it is the only one, and returns it. */
    public static RecordedCall awaitTheEnd() {
        List<RecordedCall> ends = awaitEnds(1);
        assertThat(ends).hasSize(1);
        return ends.getFirst();
    }

    public static TurnEnd endOf(RecordedCall end) {
        return ((EndTurnRequest) end.request()).getEnd();
    }

    public static void reset() {
        LedgerTurnServer.reset();
    }
}
