package bot.finance.adapter.cdc;

import java.util.Optional;

public record CategoryRow(String name, Optional<Long> parentId) {}
