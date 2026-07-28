package bot.finance.application.usecase;

import bot.finance.application.dto.NewUser;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;

public class InitializeUserUseCase implements InitializeUserPort {

    private final UserRepository userRepository;
    private final Logger log;

    public InitializeUserUseCase(UserRepository userRepository, LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.log = loggerFactory.getLogger(InitializeUserUseCase.class);
    }

    @Override
    public User initialize(NewUser newUser) {
        if (newUser == null) {
            throw new InvalidUserException("new user command is absent");
        }
        return userRepository.findByExternalId(newUser.externalId()).orElseGet(() -> createUser(newUser.externalId()));
    }

    private User createUser(String externalId) {
        User created = userRepository.create(User.newUser(externalId), Category.defaults());
        log.info("created user with external id {}", externalId);
        return created;
    }
}
