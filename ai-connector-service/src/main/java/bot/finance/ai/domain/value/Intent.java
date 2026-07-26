package bot.finance.ai.domain.value;

public sealed interface Intent permits CategoryIntent, ExpenseIntent, UnknownIntent {

}
