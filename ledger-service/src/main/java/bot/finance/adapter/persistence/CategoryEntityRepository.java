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

    boolean existsByIdAndUserIdAndParentIdIsNotNull(Long id, Long userId);

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

    @Query(
            """
            SELECT c.id AS id, c.name AS name, g.id AS grouping_id, g.name AS grouping_name
            FROM category c
            JOIN category g ON g.id = c.parent_id
            WHERE c.user_id = :userId
              AND (CAST(:groupingId AS BIGINT) IS NULL OR c.parent_id = :groupingId)
            ORDER BY g.name, c.name
            """)
    List<CategoryEntryProjection> findCategoryEntriesForUser(
            @Param("userId") Long userId, @Param("groupingId") Long groupingId);

    @Query(
            """
            SELECT id, name
            FROM category
            WHERE user_id = :userId
              AND parent_id IS NULL
            ORDER BY name
            """)
    List<GroupingEntryProjection> findGroupingEntriesForUser(@Param("userId") Long userId);
}
