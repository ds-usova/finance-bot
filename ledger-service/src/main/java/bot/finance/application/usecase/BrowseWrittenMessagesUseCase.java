package bot.finance.application.usecase;

import bot.finance.application.dto.BrowseWrittenMessagesCommand;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.BrowseWrittenMessagesPort;
import bot.finance.application.port.UserRepository;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.model.User;
import java.util.List;

public class BrowseWrittenMessagesUseCase implements BrowseWrittenMessagesPort {

    private final UserRepository userRepository;
    private final WrittenMessageRepository writtenMessageRepository;

    public BrowseWrittenMessagesUseCase(
            UserRepository userRepository, WrittenMessageRepository writtenMessageRepository) {
        this.userRepository = userRepository;
        this.writtenMessageRepository = writtenMessageRepository;
    }

    @Override
    public List<WrittenMessageView> browse(BrowseWrittenMessagesCommand command) {
        User user = userRepository.requireById(command.userId().userId());
        return writtenMessageRepository.findUnsettledForUser(user.id().orElseThrow());
    }
}
