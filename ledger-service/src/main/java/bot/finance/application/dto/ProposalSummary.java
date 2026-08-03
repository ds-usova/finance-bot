package bot.finance.application.dto;

import bot.finance.domain.value.Money;
import java.util.Optional;

public record ProposalSummary(
        String categoryName, String parentCategoryName, String description, Optional<String> merchant, Money money) {}
