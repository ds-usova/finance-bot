package bot.finance.adapter.persistence;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

public interface UserEntityRepository extends CrudRepository<UserEntity, Long> {

    Optional<UserEntity> findByExternalId(String externalId);

}
