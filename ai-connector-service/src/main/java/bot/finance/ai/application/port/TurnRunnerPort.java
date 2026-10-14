package bot.finance.ai.application.port;

import bot.finance.ai.application.dto.TurnStart;
import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.TurnEnd;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface TurnRunnerPort {

    /**
     * Runs {@code turn} apart from the caller unless a turn under {@code turnKey} is already running or the cap is
     * reached, then hands {@code end} what the turn returned, or {@link TurnEnd#UNAVAILABLE} when it outlived the
     * run's bound.
     */
    TurnStart start(MessageIdentity turnKey, Supplier<TurnEnd> turn, Consumer<TurnEnd> end);
}
