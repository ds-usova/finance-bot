package bot.finance.adapter.persistence;

import bot.finance.application.port.UserPreferenceRepository;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.CurrencyCode;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UserPreferenceRepositoryAdapter implements UserPreferenceRepository {

    private final UserPreferenceEntityRepository userPreferenceEntityRepository;

    public UserPreferenceRepositoryAdapter(UserPreferenceEntityRepository userPreferenceEntityRepository) {
        this.userPreferenceEntityRepository = userPreferenceEntityRepository;
    }

    @Override
    public Optional<CurrencyCode> findDefaultCurrency(long userId) {
        try {
            return userPreferenceEntityRepository
                    .findById(userId)
                    .map(entity -> new CurrencyCode(entity.defaultCurrencyCode()));
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find default currency for user " + userId, e);
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
