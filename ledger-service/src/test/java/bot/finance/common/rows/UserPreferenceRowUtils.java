package bot.finance.common.rows;

import bot.finance.adapter.persistence.UserPreferenceEntity;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

public class UserPreferenceRowUtils {

    private UserPreferenceRowUtils() {}

    public static void storedPreference(
            JdbcAggregateTemplate jdbcAggregateTemplate, long userId, String defaultCurrencyCode) {
        jdbcAggregateTemplate.insert(new UserPreferenceEntity(userId, defaultCurrencyCode));
    }

    public static Optional<String> storedDefaultCurrencyCode(JdbcAggregateTemplate jdbcAggregateTemplate, long userId) {
        return Optional.ofNullable(jdbcAggregateTemplate.findById(userId, UserPreferenceEntity.class))
                .map(UserPreferenceEntity::defaultCurrencyCode);
    }
}
