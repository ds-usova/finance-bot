package bot.finance.ai.domain.value;

public sealed interface RecordedChange permits SpendingRowChange, CategoryRowChange {}
