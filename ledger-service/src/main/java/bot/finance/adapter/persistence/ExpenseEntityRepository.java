package bot.finance.adapter.persistence;

import org.springframework.data.repository.CrudRepository;

public interface ExpenseEntityRepository extends CrudRepository<ExpenseEntity, Long> {}
