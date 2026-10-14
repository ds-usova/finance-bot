package bot.finance.ai.common.containers;

import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalRequest;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalResponse;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnServiceGrpc;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodRequest;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodResponse;
import com.google.protobuf.Message;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A JVM-wide gRPC server on a random real port, serving a fake {@code LedgerTurnService}. It records each request
 * with its {@code authorization} metadata and answers the outcome scripted for it: one outcome per call, in
 * order, and stored once the script is spent.
 */
public final class LedgerTurnServer {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Context.Key<String> RECEIVED_AUTHORIZATION = Context.key("receivedAuthorization");

    private static final Queue<Outcome> SCRIPT = new ConcurrentLinkedQueue<>();
    private static final List<RecordedCall> CALLS = new CopyOnWriteArrayList<>();
    private static final Server SERVER;

    static {
        SERVER = ServerBuilder.forPort(0)
                .addService(ServerInterceptors.intercept(new FakeLedgerTurnService(), new AuthorizationCapture()))
                .build();
        try {
            SERVER.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start the fake ledger turn server", e);
        }
    }

    private LedgerTurnServer() {}

    /** What the fake answers one call with. */
    public sealed interface Outcome {

        record Stored() implements Outcome {}

        record Refused(Status status) implements Outcome {}

        record NeverAnswering() implements Outcome {}
    }

    /** One request the fake received: the RPC's method name, the request itself and the credential it carried. */
    public record RecordedCall(String method, Message request, String authorization) {}

    public static int port() {
        return SERVER.getPort();
    }

    public static String target() {
        return "static://localhost:" + port();
    }

    public static void script(Outcome outcome) {
        SCRIPT.add(outcome);
    }

    public static List<RecordedCall> recordedCalls() {
        return List.copyOf(CALLS);
    }

    public static void reset() {
        SCRIPT.clear();
        CALLS.clear();
    }

    private static Outcome nextOutcome() {
        Outcome outcome = SCRIPT.poll();
        return outcome == null ? new Outcome.Stored() : outcome;
    }

    private static final class AuthorizationCapture implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            Context context = Context.current().withValue(RECEIVED_AUTHORIZATION, headers.get(AUTHORIZATION));
            return Contexts.interceptCall(context, call, headers, next);
        }
    }

    private static final class FakeLedgerTurnService extends LedgerTurnServiceGrpc.LedgerTurnServiceImplBase {

        @Override
        public void fileExpenseProposal(
                FileExpenseProposalRequest request, StreamObserver<FileExpenseProposalResponse> responseObserver) {
            CALLS.add(new RecordedCall("FileExpenseProposal", request, RECEIVED_AUTHORIZATION.get()));

            switch (nextOutcome()) {
                case Outcome.Stored stored -> {
                    FileExpenseProposalResponse.Builder response = FileExpenseProposalResponse.newBuilder()
                            .setId(1)
                            .setGrouping(request.getGrouping())
                            .setCategory(request.getCategory())
                            .setDescription(request.getDescription())
                            .setAmount(request.getAmount())
                            .setCurrencyCode(request.getCurrencyCode())
                            .setCreatedAt("2026-10-13T10:00:00Z");
                    if (request.hasMerchant()) {
                        response.setMerchant(request.getMerchant());
                    }
                    responseObserver.onNext(response.build());
                    responseObserver.onCompleted();
                }
                case Outcome.Refused refused ->
                    responseObserver.onError(refused.status().asRuntimeException());
                case Outcome.NeverAnswering neverAnswering -> {}
            }
        }

        @Override
        public void recordSpendingPeriod(
                RecordSpendingPeriodRequest request, StreamObserver<RecordSpendingPeriodResponse> responseObserver) {
            CALLS.add(new RecordedCall("RecordSpendingPeriod", request, RECEIVED_AUTHORIZATION.get()));

            switch (nextOutcome()) {
                case Outcome.Stored stored -> {
                    responseObserver.onNext(RecordSpendingPeriodResponse.newBuilder()
                            .setFrom(request.getFrom())
                            .setTo(request.getTo())
                            .build());
                    responseObserver.onCompleted();
                }
                case Outcome.Refused refused ->
                    responseObserver.onError(refused.status().asRuntimeException());
                case Outcome.NeverAnswering neverAnswering -> {}
            }
        }
    }
}
