package bot.finance.adapter.persistence;

import bot.finance.domain.model.User;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("app_user")
public record UserEntity(@Id Long id, String externalId) {

    public User toDomain() {
        return id == null ? User.newUser(externalId) : User.stored(id, externalId);
    }

    public static UserEntity fromDomain(User user) {
        return new UserEntity(user.id().orElse(null), user.externalId());
    }

}
