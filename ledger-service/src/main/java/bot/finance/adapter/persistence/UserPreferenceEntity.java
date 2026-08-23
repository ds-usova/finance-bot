package bot.finance.adapter.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("user_preference")
public record UserPreferenceEntity(@Id Long userId, String defaultCurrencyCode) {}
