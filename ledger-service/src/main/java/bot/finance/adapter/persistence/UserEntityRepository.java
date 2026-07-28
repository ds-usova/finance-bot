package bot.finance.adapter.persistence;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserEntityRepository extends CrudRepository<UserEntity, Long> {

    Optional<UserEntity> findByExternalId(String externalId);

    @Query("""
            INSERT INTO app_user (external_id) VALUES (:externalId)
            ON CONFLICT (external_id) DO NOTHING
            RETURNING id
            """)
    Optional<Long> insertIfAbsent(@Param("externalId") String externalId);

}
