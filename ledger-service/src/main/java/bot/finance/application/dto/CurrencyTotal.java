package bot.finance.application.dto;

import bot.finance.domain.value.Money;

public record CurrencyTotal(Money total, int expenseCount) {}
