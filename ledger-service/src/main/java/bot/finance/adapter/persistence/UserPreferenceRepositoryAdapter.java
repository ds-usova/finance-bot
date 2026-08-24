package bot.finance.adapter.persistence;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserPreferenceRepository;
import bot.finance.domain.exception.InvalidMoneyException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.CurrencyCode;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UserPreferenceRepositoryAdapter implements UserPreferenceRepository {

    private final UserPreferenceEntityRepository userPreferenceEntityRepository;
    private final Logger log;

    public UserPreferenceRepositoryAdapter(
            UserPreferenceEntityRepository userPreferenceEntityRepository, LoggerFactory loggerFactory) {
        this.userPreferenceEntityRepository = userPreferenceEntityRepository;
        this.log = loggerFactory.getLogger(UserPreferenceRepositoryAdapter.class);
    }

    @Override
    public Optional<CurrencyCode> findDefaultCurrency(long userId) {
        Optional<UserPreferenceEntity> row;
        try {
            row = userPreferenceEntityRepository.findById(userId);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find default currency for user " + userId, e);
        }

        return row.flatMap(this::toDefaultCurrency);
    }

    private Optional<CurrencyCode> toDefaultCurrency(UserPreferenceEntity entity) {
        try {
            return Optional.of(entity.toDefaultCurrency());
        } catch (InvalidMoneyException e) {
            log.warn("Dropping stored default currency for user {}: {}", entity.userId(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void replaceDefaultCurrency(long userId, CurrencyCode defaultCurrency) {
        try {
            userPreferenceEntityRepository.upsertDefaultCurrencyCode(userId, defaultCurrency.code());
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to replace default currency for user " + userId, e);
        }
    }
}
