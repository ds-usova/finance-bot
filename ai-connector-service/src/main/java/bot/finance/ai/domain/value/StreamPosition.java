package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;

public record StreamPosition(long ms, long seq) implements Comparable<StreamPosition> {

    public StreamPosition {
        if (ms <= 0) {
            throw new InvalidValueException("Ms must be positive");
        }
        if (seq < 0) {
            throw new InvalidValueException("Seq must not be negative");
        }
    }

    @Override
    public int compareTo(StreamPosition other) {
        int msComparison = Long.compare(ms, other.ms);
        if (msComparison != 0) {
            return msComparison;
        }
        return Long.compare(seq, other.seq);
    }
}
