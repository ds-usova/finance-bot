package bot.finance.common.fixtures;

import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalRequest;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnServiceGrpc;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodRequest;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Calls the ledger's turn operations over a real gRPC channel to {@code localhost}, the way the AI Connector
 * does, and builds the requests a test sends. The channel is closed once the call has returned or failed.
 */
public final class TurnOperationCalls {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private TurnOperationCalls() {}

    /** Calls with no credential at all. */
    public static <T> T call(int port, Function<LedgerTurnServiceGrpc.LedgerTurnServiceBlockingStub, T> call) {
        return call(port, null, call);
    }

    /** Calls with {@code authorization: Bearer <token>} as metadata; a null token sends no credential. */
    public static <T> T call(
            int port, String token, Function<LedgerTurnServiceGrpc.LedgerTurnServiceBlockingStub, T> call) {
        ManagedChannel channel = Grpc.newChannelBuilder("localhost:" + port, InsecureChannelCredentials.create())
                .build();
        try {
            LedgerTurnServiceGrpc.LedgerTurnServiceBlockingStub stub = LedgerTurnServiceGrpc.newBlockingStub(channel);
            if (token != null) {
                Metadata headers = new Metadata();
                headers.put(AUTHORIZATION, "Bearer " + token);
                stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
            }
            return call.apply(stub);
        } finally {
            channel.shutdownNow();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** A null merchant is left unset. */
    public static FileExpenseProposalRequest fileExpense(
            String grouping, String category, String description, String merchant, String amount, String currencyCode) {
        FileExpenseProposalRequest.Builder builder = FileExpenseProposalRequest.newBuilder()
                .setGrouping(grouping)
                .setCategory(category)
                .setDescription(description)
                .setAmount(amount)
                .setCurrencyCode(currencyCode);
        if (merchant != null) {
            builder.setMerchant(merchant);
        }
        return builder.build();
    }

    public static RecordSpendingPeriodRequest recordPeriod(String from, String to) {
        return RecordSpendingPeriodRequest.newBuilder().setFrom(from).setTo(to).build();
    }
}
