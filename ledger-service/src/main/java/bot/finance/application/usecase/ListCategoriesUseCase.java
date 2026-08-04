package bot.finance.application.usecase;

import bot.finance.application.dto.ListCategoriesCommand;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.ListCategoriesPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import java.util.List;

public class ListCategoriesUseCase implements ListCategoriesPort {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final Logger log;

    public ListCategoriesUseCase(
            UserRepository userRepository, CategoryRepository categoryRepository, LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.log = loggerFactory.getLogger(ListCategoriesUseCase.class);
    }

    @Override
    public List<String> list(ListCategoriesCommand command) {
        // refuses an absent command, resolves the token's subject to a stored user, finds the caller's
        // categories carrying the name, refuses when none is a grouping, and answers that grouping's
        // child names ordered by name
        return null;
    }
}
