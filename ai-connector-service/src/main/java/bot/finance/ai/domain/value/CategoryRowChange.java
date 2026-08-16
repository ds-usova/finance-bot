package bot.finance.ai.domain.value;

import java.util.Optional;

public record CategoryRowChange(ChangeOperation op, Optional<CategoryRow> before, Optional<CategoryRow> after)
        implements RecordedChange {

    public CategoryRowChange {}
}
