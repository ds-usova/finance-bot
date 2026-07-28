package bot.finance.adapter.persistence;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserEntityRepository extends CrudRepository<UserEntity, Long> {

    Optional<UserEntity> findByExternalId(String externalId);

}
