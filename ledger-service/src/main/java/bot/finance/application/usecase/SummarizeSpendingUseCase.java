package bot.finance.application.usecase;

import bot.finance.application.dto.SummarizeSpendingCommand;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.SummarizeSpendingPort;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.InvalidSpendingQueryException;
import bot.finance.domain.model.SpendingQuery;
import bot.finance.domain.model.User;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Clock;
import java.time.Instant;

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
        if (command == null) {
            throw new InvalidSpendingQueryException("summarize spending command is absent");
        }

        SpendingPeriod period = SpendingPeriod.of(command.from(), command.to());
        User user = userRepository.requireByExternalId(command.userId().externalId());
        long userId = user.id().orElseThrow();
        Instant now = clock.instant();
        spendingQueryRepository.create(SpendingQuery.newQuery(userId, period, command.reference(), now));

        return period;
    }
}
