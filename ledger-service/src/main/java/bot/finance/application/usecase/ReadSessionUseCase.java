package bot.finance.application.usecase;

import bot.finance.application.dto.ReadSessionCommand;
import bot.finance.application.port.ReadSessionPort;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.model.User;

public class ReadSessionUseCase implements ReadSessionPort {

    private final UserRepository userRepository;

    public ReadSessionUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User read(ReadSessionCommand command) {
        if (command == null) {
            throw new InvalidUserException("session command is absent");
        }

        return userRepository.requireById(command.userId().userId());
    }
}
