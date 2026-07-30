package bot.finance.adapter.persistence;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface CategoryEntityRepository extends CrudRepository<CategoryEntity, Long> {

    List<CategoryEntity> findByUserIdAndName(Long userId, String name);

    List<CategoryEntity> findByParentId(Long parentId);
}
