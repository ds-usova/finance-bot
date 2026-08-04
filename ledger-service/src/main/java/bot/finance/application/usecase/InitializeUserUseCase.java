package bot.finance.application.usecase;

import bot.finance.application.dto.InitializeUserCommand;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Grouping;

public class InitializeUserUseCase implements InitializeUserPort {

    private final UserRepository userRepository;
    private final Logger log;

    public InitializeUserUseCase(UserRepository userRepository, LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.log = loggerFactory.getLogger(InitializeUserUseCase.class);
    }

    @Override
    public User initialize(InitializeUserCommand command) {
        if (command == null) {
            throw new InvalidUserException("new user command is absent");
        }
        return userRepository.findByExternalId(command.externalId()).orElseGet(() -> createUser(command.externalId()));
    }

    private User createUser(String externalId) {
        User created = userRepository.create(User.newUser(externalId), Grouping.defaults());
        log.info("created user with external id {}", externalId);
        return created;
    }
}
