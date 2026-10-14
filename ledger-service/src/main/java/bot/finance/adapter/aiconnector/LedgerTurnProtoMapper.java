package bot.finance.adapter.aiconnector;

import bot.finance.adapter.mcp.CreateExpenseProposalToolRequest;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.SpendingPeriod;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalRequest;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalResponse;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodResponse;
import java.time.format.DateTimeFormatter;

public final class LedgerTurnProtoMapper {

    private LedgerTurnProtoMapper() {}

    public static CreateExpenseProposalToolRequest toToolRequest(FileExpenseProposalRequest request) {
        return new CreateExpenseProposalToolRequest(
                request.getCategory(),
                request.getGrouping(),
                request.getDescription(),
                request.hasMerchant() ? request.getMerchant() : null,
                request.getAmount(),
                request.getCurrencyCode());
    }

    public static FileExpenseProposalResponse toResponse(Expense stored, String grouping, String category) {
        FileExpenseProposalResponse.Builder response = FileExpenseProposalResponse.newBuilder()
                .setId(stored.id().orElseThrow())
                .setGrouping(grouping)
                .setCategory(category)
                .setDescription(stored.description())
                .setAmount(stored.money().amount().toPlainString())
                .setCurrencyCode(stored.money().currencyCode().code())
                .setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(stored.createdAt()));
        stored.merchant().ifPresent(response::setMerchant);
        return response.build();
    }

    public static RecordSpendingPeriodResponse toResponse(SpendingPeriod period) {
        return RecordSpendingPeriodResponse.newBuilder()
                .setFrom(period.from().toString())
                .setTo(period.to().toString())
                .build();
    }
}
