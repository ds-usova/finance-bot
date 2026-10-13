package bot.finance.adapter.aiconnector;

import bot.finance.adapter.mcp.CreateExpenseProposalToolRequest;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.SpendingPeriod;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalRequest;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalResponse;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodResponse;

public final class LedgerTurnProtoMapper {

    private LedgerTurnProtoMapper() {}

    public static CreateExpenseProposalToolRequest toToolRequest(FileExpenseProposalRequest request) {
        // stub-intent: copies the request's fields into the tools' request, an absent merchant as null
        return null;
    }

    public static FileExpenseProposalResponse toResponse(Expense stored, String grouping, String category) {
        // stub-intent: answers the stored entry with the grouping and category it was filed under, its amount as
        // a plain decimal, created_at as RFC 3339 in UTC, and merchant only where the entry has one
        return null;
    }

    public static RecordSpendingPeriodResponse toResponse(SpendingPeriod period) {
        // stub-intent: answers the period's two days written YYYY-MM-DD
        return null;
    }
}
