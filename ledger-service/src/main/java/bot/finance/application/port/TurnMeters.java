package bot.finance.application.port;

import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.TurnChannel;
import java.time.Duration;

/**
 * Counts a delivered turn by its outcome, a resolved proposal by its resolution and a swept turn by its channel, and
 * records the age of the oldest queued turn of a channel, so the pipeline's throughput, failure rate and backlog are
 * visible without reading logs.
 */
public interface TurnMeters {

    void countTurn(ReportOutcome outcome);

    void countUnreported();

    void countResolved(ProposalResolution resolution, int count);

    void countSwept(TurnChannel channel);

    void recordOldestQueued(TurnChannel channel, Duration age);
}
