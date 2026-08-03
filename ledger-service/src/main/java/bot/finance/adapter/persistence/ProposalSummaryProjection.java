package bot.finance.adapter.persistence;

import bot.finance.application.dto.ProposalSummary;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import java.util.Optional;

public record ProposalSummaryProjection(
        String categoryName,
        String parentName,
        String description,
        String merchant,
        long amountMinorUnits,
        String currencyCode) {

    public ProposalSummary toSummary() {
        return new ProposalSummary(
                categoryName,
                parentName,
                description,
                Optional.ofNullable(merchant),
                new Money(amountMinorUnits, CurrencyCode.of(currencyCode)));
    }
}
