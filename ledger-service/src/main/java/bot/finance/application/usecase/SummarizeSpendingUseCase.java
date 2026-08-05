package bot.finance.application.usecase;

import bot.finance.application.dto.SummarizeSpendingCommand;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.SummarizeSpendingPort;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Clock;

public class SummarizeSpendingUseCase implements SummarizeSpendingPort {

    private final UserRepository userRepository;
    private final SpendingQueryRepository spendingQueryRepository;
    private final Clock clock;

    public SummarizeSpendingUseCase(
            UserRepository userRepository, SpendingQueryRepository spendingQueryRepository, Clock clock) {
        this.userRepository = userRepository;
        this.spendingQueryRepository = spendingQueryRepository;
        this.clock = clock;
    }

    @Override
    public SpendingPeriod summarize(SummarizeSpendingCommand command) {
        // refuses an absent command, parses the written dates into a SpendingPeriod, resolves the token's
        // subject to a stored user, stores a SpendingQuery under the command's reference, and answers the
        // period it accepted
        return null;
    }
}
