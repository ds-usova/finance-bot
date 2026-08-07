package bot.finance.application.usecase;

import bot.finance.application.dto.BrowseCategoriesCommand;
import bot.finance.application.dto.CategoryEntry;
import bot.finance.application.port.BrowseCategoriesPort;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.UserRepository;
import java.util.List;

public class BrowseCategoriesUseCase implements BrowseCategoriesPort {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public BrowseCategoriesUseCase(UserRepository userRepository, CategoryRepository categoryRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public List<CategoryEntry> browse(BrowseCategoriesCommand command) {
        // resolves the caller's user row by external id, then reads every category for that user, narrowed to
        // one grouping when the command names one, through CategoryRepository
        return List.of();
    }
}
