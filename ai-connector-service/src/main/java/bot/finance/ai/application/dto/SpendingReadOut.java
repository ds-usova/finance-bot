package bot.finance.ai.application.dto;

import java.util.List;

public record SpendingReadOut(List<ReadOutExpense> expenses, List<ReadOutPeriod> periods) {}
