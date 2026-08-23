package bot.finance.application.usecase;

import bot.finance.application.dto.Preferences;
import bot.finance.application.dto.ReplacePreferencesCommand;
import bot.finance.application.port.ReplacePreferencesPort;
import bot.finance.application.port.UserPreferenceRepository;
import bot.finance.application.port.UserRepository;

public class ReplacePreferencesUseCase implements ReplacePreferencesPort {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    public ReplacePreferencesUseCase(UserRepository userRepository, UserPreferenceRepository userPreferenceRepository) {
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
    }

    @Override
    public Preferences replace(ReplacePreferencesCommand command) {
        // resolves the caller through UserRepository, writes the command's currency through
        // UserPreferenceRepository for that caller's id, then answers preferences carrying it back
        return null;
    }
}
