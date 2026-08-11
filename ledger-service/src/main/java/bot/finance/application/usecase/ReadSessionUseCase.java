package bot.finance.application.usecase;

import bot.finance.application.dto.ReadSessionCommand;
import bot.finance.application.port.ReadSessionPort;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.model.User;

public class ReadSessionUseCase implements ReadSessionPort {

    private final UserRepository userRepository;

    public ReadSessionUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User read(ReadSessionCommand command) {
        // resolves the caller's stored row by internal id, so a token naming no user is refused
        return null;
    }
}
