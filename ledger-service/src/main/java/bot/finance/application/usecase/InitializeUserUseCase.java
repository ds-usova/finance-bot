package bot.finance.application.usecase;

import bot.finance.application.dto.NewUser;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.model.User;

public class InitializeUserUseCase implements InitializeUserPort {

    private final UserRepository userRepository;
    private final Logger log;

    public InitializeUserUseCase(UserRepository userRepository, LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.log = loggerFactory.getLogger(InitializeUserUseCase.class);
    }

    @Override
    public User initialize(NewUser newUser) {
        // rejects an absent command; returns the user already stored under the external id,
        // otherwise creates it together with Category.defaults() and returns what was stored
        return null;
    }

}
