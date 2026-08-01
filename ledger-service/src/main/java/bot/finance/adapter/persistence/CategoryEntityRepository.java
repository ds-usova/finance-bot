package bot.finance.adapter.persistence;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface CategoryEntityRepository extends CrudRepository<CategoryEntity, Long> {

    List<CategoryEntity> findByUserIdAndName(Long userId, String name);

    List<CategoryEntity> findByParentId(Long parentId);

    @Query(
            """
            SELECT c.name AS name, p.name AS parent_name
            FROM category c
            JOIN category p ON c.parent_id = p.id
            WHERE c.user_id = :userId
            """)
    List<KnownCategoryProjection> findKnownCategories(@Param("userId") Long userId);
}
