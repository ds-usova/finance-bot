package bot.finance.application.port;

import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ReportOutcome;

/**
 * Counts a delivered turn by its outcome and a resolved proposal by its resolution, so the pipeline's throughput
 * and failure rate are visible without reading logs.
 */
public interface TurnMeters {

    void countTurn(ReportOutcome outcome);

    void countUnreported();

    void countResolved(ProposalResolution resolution, int count);
}
