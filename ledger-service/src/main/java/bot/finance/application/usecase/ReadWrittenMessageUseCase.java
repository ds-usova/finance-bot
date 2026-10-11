package bot.finance.application.usecase;

import bot.finance.application.dto.ReadWrittenMessageCommand;
import bot.finance.application.dto.WrittenMessageAnswer;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.ReadWrittenMessagePort;
import bot.finance.application.port.UserRepository;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.exception.WrittenMessageNotFoundException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.LocalDate;
import java.util.List;

public class ReadWrittenMessageUseCase implements ReadWrittenMessagePort {

    private final UserRepository userRepository;
    private final WrittenMessageRepository writtenMessageRepository;
    private final ExpenseRepository expenseRepository;

    public ReadWrittenMessageUseCase(
            UserRepository userRepository,
            WrittenMessageRepository writtenMessageRepository,
            ExpenseRepository expenseRepository) {
        this.userRepository = userRepository;
        this.writtenMessageRepository = writtenMessageRepository;
        this.expenseRepository = expenseRepository;
    }

    @Override
    public WrittenMessageAnswer read(ReadWrittenMessageCommand command) {
        User user = userRepository.requireById(command.userId().userId());
        long userId = user.id().orElseThrow();
        WrittenMessageView message = writtenMessageRepository
                .findForUser(userId, command.messageId())
                .orElseThrow(() -> new WrittenMessageNotFoundException(
                        "no written message " + command.messageId() + " for the caller"));
        if (message.status() != WrittenMessageStatus.DONE) {
            return new WrittenMessageAnswer(message, List.of());
        }
        List<LocalDate> days = expenseRepository.findDaysByMessageReference(userId, message.incomingMessageId());
        return new WrittenMessageAnswer(message, days);
    }
}
