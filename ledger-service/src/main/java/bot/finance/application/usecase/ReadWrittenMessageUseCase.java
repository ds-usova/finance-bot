package bot.finance.application.usecase;

import bot.finance.application.dto.ReadWrittenMessageCommand;
import bot.finance.application.dto.WrittenMessageAnswer;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.ReadWrittenMessagePort;
import bot.finance.application.port.UserRepository;
import bot.finance.application.port.WrittenMessageRepository;

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
        // stub-intent: resolve the caller, find their message or refuse with WrittenMessageNotFoundException, and
        // read its days back from the expense store only once the message is DONE, answering no days before then.
        return null;
    }
}
