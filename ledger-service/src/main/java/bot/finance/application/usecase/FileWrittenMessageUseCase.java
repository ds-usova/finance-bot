package bot.finance.application.usecase;

import bot.finance.application.dto.FileWrittenMessageCommand;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.FileWrittenMessagePort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.application.port.WrittenMessageReadingDispatchPort;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.model.User;
import bot.finance.domain.model.WrittenMessage;
import java.time.Clock;

public class FileWrittenMessageUseCase implements FileWrittenMessagePort {

    private final UserRepository userRepository;
    private final WrittenMessageRepository writtenMessageRepository;
    private final WrittenMessageReadingDispatchPort dispatchPort;
    private final Clock clock;
    private final Logger log;

    public FileWrittenMessageUseCase(
            UserRepository userRepository,
            WrittenMessageRepository writtenMessageRepository,
            WrittenMessageReadingDispatchPort dispatchPort,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.writtenMessageRepository = writtenMessageRepository;
        this.dispatchPort = dispatchPort;
        this.clock = clock;
        this.log = loggerFactory.getLogger(FileWrittenMessageUseCase.class);
    }

    @Override
    public WrittenMessageView file(FileWrittenMessageCommand command) {
        if (command == null) {
            throw new InvalidWrittenMessageException("file written message command must be present");
        }
        User user = userRepository.requireById(command.userId().userId());
        WrittenMessage message = WrittenMessage.newMessage(user.id().orElseThrow(), command.text(), clock.instant());

        WrittenMessageView stored = writtenMessageRepository.store(message);
        dispatchPort.dispatch();
        return stored;
    }
}
