package bot.finance.application.usecase;

import bot.finance.application.dto.Preferences;
import bot.finance.application.dto.ReadPreferencesCommand;
import bot.finance.application.port.ReadPreferencesPort;
import bot.finance.application.port.UserPreferenceRepository;
import bot.finance.application.port.UserRepository;

public class ReadPreferencesUseCase implements ReadPreferencesPort {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    public ReadPreferencesUseCase(UserRepository userRepository, UserPreferenceRepository userPreferenceRepository) {
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
    }

    @Override
    public Preferences read(ReadPreferencesCommand command) {
        long userId = userRepository.requireById(command.userId().userId()).id().orElseThrow();

        return new Preferences(userPreferenceRepository.findDefaultCurrency(userId));
    }
}
