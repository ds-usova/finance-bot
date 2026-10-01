package bot.finance.adapter.persistence;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface UserPreferenceEntityRepository extends CrudRepository<UserPreferenceEntity, Long> {

    @Modifying
    @Query(
            """
            INSERT INTO user_preference (user_id, default_currency_code) VALUES (:userId, :code)
            ON CONFLICT (user_id) DO UPDATE SET default_currency_code = EXCLUDED.default_currency_code
            """)
    int upsertDefaultCurrencyCode(@Param("userId") Long userId, @Param("code") String code);
}
