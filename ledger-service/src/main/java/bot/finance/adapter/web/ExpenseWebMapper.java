package bot.finance.adapter.web;

import bot.finance.api.model.Expense;
import bot.finance.api.model.ListExpenses200Response;
import bot.finance.application.dto.DayTotal;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.dto.ExpensePage;
import bot.finance.domain.exception.InvalidExpenseFilterException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.value.ExpenseFilter;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.SpendingPeriod;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class ExpenseWebMapper {

    private ExpenseWebMapper() {}

    public static ExpenseFilter toFilter(
            Integer limit, Integer offset, String status, Long categoryId, LocalDate from, LocalDate to) {
        ExpenseStatus parsedStatus = toStatus(status);
        SpendingPeriod period = toPeriod(from, to);

        return new ExpenseFilter(
                parsedStatus,
                categoryId,
                period,
                limit == null ? ExpenseFilter.DEFAULT_LIMIT : limit,
                offset == null ? 0 : offset);
    }

    public static ListExpenses200Response toResponse(ExpensePage page) {
        return new ListExpenses200Response(
                page.items().stream().map(ExpenseWebMapper::toItem).toList(),
                page.limit(),
                page.offset(),
                page.total(),
                page.dayTotals().stream().map(ExpenseWebMapper::toDayTotal).toList());
    }

    private static ExpenseStatus toStatus(String status) {
        if (status == null) {
            return null;
        }

        try {
            return ExpenseStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new InvalidExpenseFilterException("status must be PENDING or RECORDED");
        }
    }

    private static SpendingPeriod toPeriod(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        if (from == null || to == null) {
            throw new InvalidSpendingPeriodException("A period needs both a from and a to day, or neither");
        }

        return new SpendingPeriod(from, to);
    }

    private static Expense toItem(ExpenseEntry entry) {
        Expense item = new Expense(
                entry.id(),
                bot.finance.api.model.ExpenseStatus.valueOf(entry.status().name()),
                entry.categoryId(),
                entry.description(),
                MoneyRenderer.render(entry.money()),
                entry.createdAt().atOffset(ZoneOffset.UTC));
        entry.merchant().ifPresent(item::merchant);

        return item;
    }

    private static bot.finance.api.model.DayTotal toDayTotal(DayTotal dayTotal) {
        return new bot.finance.api.model.DayTotal(
                dayTotal.day(),
                dayTotal.amounts().stream().map(MoneyRenderer::render).toList());
    }
}
