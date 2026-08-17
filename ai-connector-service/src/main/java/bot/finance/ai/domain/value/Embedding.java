package bot.finance.ai.domain.value;

import java.util.List;

public record Embedding(List<Float> values) {

    public Embedding {
        // TODO: refuse a null or empty list and a null element, as InvalidValueException, and copy the list so a
        // later mutation of it cannot reach the stored value
    }
}
