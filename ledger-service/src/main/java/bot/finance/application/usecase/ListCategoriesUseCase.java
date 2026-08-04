package bot.finance.application.usecase;

import bot.finance.application.dto.ListCategoriesCommand;
import bot.finance.application.dto.StoredGrouping;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.ListCategoriesPort;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.model.User;
import java.util.List;
import java.util.Optional;

public class ListCategoriesUseCase implements ListCategoriesPort {

    private final UserRepository userRepository;
    private final GroupingRepository groupingRepository;
    private final CategoryRepository categoryRepository;

    public ListCategoriesUseCase(
            UserRepository userRepository,
            GroupingRepository groupingRepository,
            CategoryRepository categoryRepository) {
        this.userRepository = userRepository;
        this.groupingRepository = groupingRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public List<String> list(ListCategoriesCommand command) {
        if (command == null) {
            throw new InvalidGroupingException("list categories command is absent");
        }
        User user = userRepository
                .findByExternalId(command.userId().externalId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "user",
                        "no user stored under external id " + command.userId().externalId()));
        long userId = user.id().orElseThrow();
        // TODO(GU07): rework grouping resolution and its two refusals against
        // GroupingRepository/CategoryRepository per ListCategoriesUseCaseTest.
        StoredGrouping grouping = resolveGrouping(userId, command.groupingName());
        return groupingRepository.findCategoryNames(userId, grouping);
    }

    private StoredGrouping resolveGrouping(long userId, String groupingName) {
        Optional<StoredGrouping> grouping = groupingRepository.findByUserIdAndName(userId, groupingName);
        if (grouping.isPresent()) {
            return grouping.get();
        }
        if (categoryRepository.existsByUserIdAndName(userId, groupingName)) {
            throw new InvalidGroupingException(groupingName + " is a category, not a grouping");
        }
        throw new InvalidGroupingException("no grouping named " + groupingName + " is stored for this user");
    }
}
