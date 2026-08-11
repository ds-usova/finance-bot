package bot.finance.application.usecase;

import bot.finance.application.dto.BrowseGroupingsCommand;
import bot.finance.application.dto.GroupingEntry;
import bot.finance.application.port.BrowseGroupingsPort;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.model.User;
import java.util.List;

public class BrowseGroupingsUseCase implements BrowseGroupingsPort {

    private final UserRepository userRepository;
    private final GroupingRepository groupingRepository;

    public BrowseGroupingsUseCase(UserRepository userRepository, GroupingRepository groupingRepository) {
        this.userRepository = userRepository;
        this.groupingRepository = groupingRepository;
    }

    @Override
    public List<GroupingEntry> browse(BrowseGroupingsCommand command) {
        User user = userRepository.requireById(command.userId().userId());

        return groupingRepository.findAllForUser(user.id().orElseThrow());
    }
}
