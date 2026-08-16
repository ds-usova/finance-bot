package bot.finance.ai.domain.value;

import java.util.Optional;

public record CategoryRow(long id, long userId, Optional<Long> parentId, String name) {

    public CategoryRow {}
}
