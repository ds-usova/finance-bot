package bot.finance.application.usecase;

import bot.finance.application.dto.Preferences;
import bot.finance.application.dto.ReplacePreferencesCommand;
import bot.finance.application.port.ReplacePreferencesPort;
import bot.finance.application.port.UserPreferenceRepository;
import bot.finance.application.port.UserRepository;
import java.util.Optional;

public class ReplacePreferencesUseCase implements ReplacePreferencesPort {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    public ReplacePreferencesUseCase(UserRepository userRepository, UserPreferenceRepository userPreferenceRepository) {
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
    }

    @Override
    public Preferences replace(ReplacePreferencesCommand command) {
        long userId = userRepository.requireById(command.userId().userId()).id().orElseThrow();

        userPreferenceRepository.replaceDefaultCurrency(userId, command.defaultCurrency());
        return new Preferences(Optional.of(command.defaultCurrency()));
    }
}
