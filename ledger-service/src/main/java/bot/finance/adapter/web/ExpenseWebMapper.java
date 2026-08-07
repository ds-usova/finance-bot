package bot.finance.adapter.web;

import bot.finance.api.model.ListExpenses200Response;
import bot.finance.application.dto.ExpensePage;
import bot.finance.domain.value.ExpenseFilter;
import java.time.LocalDate;

public final class ExpenseWebMapper {

    private ExpenseWebMapper() {}

    public static ExpenseFilter toFilter(
            Integer limit, Integer offset, String status, Long categoryId, LocalDate from, LocalDate to) {
        // TODO: parse status into ExpenseStatus, build a SpendingPeriod from from and to when both are present
        // (InvalidSpendingPeriodException when only one is), default limit to ExpenseFilter.DEFAULT_LIMIT and
        // offset to zero when absent
        return null;
    }

    public static ListExpenses200Response toResponse(ExpensePage page) {
        // TODO: map every ExpenseEntry into a ListExpenses200ResponseItemsInner, carrying merchant only when
        // present, and copy limit, offset and total from the page
        return null;
    }
}
