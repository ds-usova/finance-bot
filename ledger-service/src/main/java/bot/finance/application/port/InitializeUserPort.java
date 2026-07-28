package bot.finance.application.port;

import bot.finance.application.dto.NewUser;
import bot.finance.domain.model.User;

public interface InitializeUserPort {

    User initialize(NewUser newUser);

}
