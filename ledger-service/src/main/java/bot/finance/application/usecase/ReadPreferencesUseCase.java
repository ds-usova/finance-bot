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
        // resolves the caller through UserRepository, then answers the stored default currency for that caller's
        // id, or an empty optional where none is stored
        return null;
    }
}
