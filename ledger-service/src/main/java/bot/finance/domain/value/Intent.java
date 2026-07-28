package bot.finance.domain.value;

public sealed interface Intent permits CategoryIntent, ExpenseIntent, UnknownIntent {}
