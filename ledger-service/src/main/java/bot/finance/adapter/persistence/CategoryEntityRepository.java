package bot.finance.adapter.persistence;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface CategoryEntityRepository extends CrudRepository<CategoryEntity, Long> {

    List<CategoryEntity> findByUserIdAndName(Long userId, String name);

    List<CategoryEntity> findByParentIdOrderByName(Long parentId);

    @Query(
            """
            SELECT c.name
            FROM category c
            WHERE c.user_id = :userId AND c.parent_id IS NULL
            ORDER BY c.name
            """)
    List<String> findGroupingNames(@Param("userId") Long userId);
}
