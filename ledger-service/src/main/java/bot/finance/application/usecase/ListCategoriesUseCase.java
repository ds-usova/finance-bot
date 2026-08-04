package bot.finance.application.usecase;

import bot.finance.application.dto.ListCategoriesCommand;
import bot.finance.application.dto.StoredCategory;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.ListCategoriesPort;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.model.User;
import java.util.List;

public class ListCategoriesUseCase implements ListCategoriesPort {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public ListCategoriesUseCase(UserRepository userRepository, CategoryRepository categoryRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public List<String> list(ListCategoriesCommand command) {
        if (command == null) {
            throw new InvalidCategoryException("list categories command is absent");
        }
        User user = userRepository
                .findByExternalId(command.userId().externalId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "user",
                        "no user stored under external id " + command.userId().externalId()));
        StoredCategory grouping = resolveGrouping(user, command.parentCategoryName());
        return categoryRepository.findChildNames(grouping.id());
    }

    private StoredCategory resolveGrouping(User user, String groupingName) {
        List<StoredCategory> candidates =
                categoryRepository.findByUserIdAndName(user.id().orElseThrow(), groupingName);
        if (candidates.isEmpty()) {
            throw new InvalidCategoryException("no grouping named " + groupingName + " is stored for this user");
        }
        return candidates.stream()
                .filter(candidate -> candidate.parentName().isEmpty())
                .findFirst()
                .orElseThrow(() -> new InvalidCategoryException(groupingName + " is a category, not a grouping"));
    }
}
