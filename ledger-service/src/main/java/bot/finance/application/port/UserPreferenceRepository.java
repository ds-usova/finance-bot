package bot.finance.application.port;

import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.CurrencyCode;
import java.util.Optional;

public interface UserPreferenceRepository {

    /**
     * @throws PersistenceFailedException if the lookup fails
     */
    Optional<CurrencyCode> findDefaultCurrency(long userId);

    /**
     * @throws PersistenceFailedException if the write fails
     */
    void replaceDefaultCurrency(long userId, CurrencyCode defaultCurrency);
}
