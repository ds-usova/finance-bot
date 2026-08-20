package bot.finance.ai.adapter.persistence;

import java.time.Instant;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface StreamEntryFailureEntityRepository extends CrudRepository<StreamEntryFailureEntity, String> {

    @Query(
            """
            INSERT INTO stream_entry_failure (entry_id, attempts, first_failed_at, last_error)
            VALUES (:entryId, 1, :now, :error)
            ON CONFLICT (entry_id) DO UPDATE
            SET attempts = stream_entry_failure.attempts + 1, last_error = EXCLUDED.last_error
            RETURNING attempts
            """)
    int countFailure(@Param("entryId") String entryId, @Param("error") String error, @Param("now") Instant now);
}
