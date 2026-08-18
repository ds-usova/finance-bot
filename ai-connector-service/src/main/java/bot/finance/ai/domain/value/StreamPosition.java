package bot.finance.ai.domain.value;

public record StreamPosition(long ms, long seq) implements Comparable<StreamPosition> {

    public StreamPosition {
        // TODO: reject a ms of zero or below, or a negative seq
    }

    @Override
    public int compareTo(StreamPosition other) {
        // Intent: order by ms first, then by seq - never by a text comparison of the pair.
        return 0;
    }
}
