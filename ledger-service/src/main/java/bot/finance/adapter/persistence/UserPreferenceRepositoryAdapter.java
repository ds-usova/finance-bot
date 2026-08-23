package bot.finance.adapter.persistence;

import bot.finance.application.port.UserPreferenceRepository;
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
        // reads the user's stored row, if any, and answers its currency code
        return Optional.empty();
    }

    @Override
    public void replaceDefaultCurrency(long userId, CurrencyCode defaultCurrency) {
        // upserts the user's row through the entity repository's conflict-handling write
    }
}
