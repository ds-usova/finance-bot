package bot.finance.adapter.persistence;

import bot.finance.domain.value.CurrencyCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("user_preference")
public record UserPreferenceEntity(@Id Long userId, String defaultCurrencyCode) {

    public CurrencyCode toDefaultCurrency() {
        return new CurrencyCode(defaultCurrencyCode);
    }
}
