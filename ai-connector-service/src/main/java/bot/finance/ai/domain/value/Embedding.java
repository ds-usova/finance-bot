package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.List;
import java.util.Objects;

public record Embedding(List<Float> values) {

    public Embedding {
        if (values == null || values.isEmpty()) {
            throw new InvalidValueException("Values must not be null or empty");
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new InvalidValueException("Values must not hold a null element");
        }
        values = List.copyOf(values);
    }
}
