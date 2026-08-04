package bot.finance.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface CategoryEntityRepository extends CrudRepository<CategoryEntity, Long> {

    Optional<CategoryEntity> findByUserIdAndNameAndParentIdIsNull(Long userId, String name);

    Optional<CategoryEntity> findByUserIdAndParentIdAndName(Long userId, Long parentId, String name);

    List<CategoryEntity> findByUserIdAndParentIdOrderByName(Long userId, Long parentId);

    boolean existsByUserIdAndNameAndParentIdIsNotNull(Long userId, String name);

    @Query(
            """
            SELECT c.name
            FROM category c
            WHERE c.user_id = :userId
              AND c.parent_id IS NULL
              AND EXISTS (SELECT 1 FROM category child WHERE child.parent_id = c.id)
            ORDER BY c.name
            """)
    List<String> findNonEmptyGroupingNames(@Param("userId") Long userId);
}
