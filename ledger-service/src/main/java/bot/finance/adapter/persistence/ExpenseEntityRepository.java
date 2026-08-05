package bot.finance.adapter.persistence;

import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ExpenseEntityRepository extends CrudRepository<ExpenseEntity, Long> {

    @Query(
            """
            SELECT count(*) FROM expense
            WHERE user_id = :userId AND message_reference = :messageReference
            """)
    int countByMessageReference(@Param("userId") Long userId, @Param("messageReference") UUID messageReference);
}
