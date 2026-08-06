package bot.finance.common.rows;

import bot.finance.adapter.persistence.UserEntity;
import bot.finance.adapter.persistence.UserEntityRepository;

public class UserRowUtils {

    private UserRowUtils() {}

    public static long storedUserId(UserEntityRepository repository, String externalId) {
        return repository.save(new UserEntity(null, externalId)).id();
    }
}
