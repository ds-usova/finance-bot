package bot.finance.common.containers;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicReference;

public class GrpcStubServer {

    public static final Server SERVER;

    private static final AtomicReference<ExtractIntentsRequest> LAST_EXTRACTION_REQUEST = new AtomicReference<>();
    private static final AtomicReference<HealthCheckRequest> LAST_HEALTH_CHECK_REQUEST = new AtomicReference<>();

    private static volatile ExtractIntentsResponse extractionResponse = ExtractIntentsResponse.getDefaultInstance();
    private static volatile Status extractionFailure;
    private static volatile HealthCheckResponse.ServingStatus servingStatus = HealthCheckResponse.ServingStatus.SERVING;
    private static volatile Status healthFailure;

    static {
        try {
            SERVER = ServerBuilder.forPort(0)
                    .addService(new StubIntentExtractionService())
                    .addService(new StubHealthService())
                    .build()
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private GrpcStubServer() {}

    public static String target() {
        return "static://localhost:" + SERVER.getPort();
    }

    public static void answerExtractionWith(ExtractIntentsResponse response) {
        extractionResponse = response;
        extractionFailure = null;
    }

    public static void failExtractionWith(Status status) {
        extractionFailure = status;
    }

    public static ExtractIntentsRequest lastExtractionRequest() {
        return LAST_EXTRACTION_REQUEST.get();
    }

    public static void reportServingStatus(HealthCheckResponse.ServingStatus status) {
        servingStatus = status;
        healthFailure = null;
    }

    public static void failHealthCheckWith(Status status) {
        healthFailure = status;
    }

    public static HealthCheckRequest lastHealthCheckRequest() {
        return LAST_HEALTH_CHECK_REQUEST.get();
    }

    public static void reset() {
        LAST_EXTRACTION_REQUEST.set(null);
        LAST_HEALTH_CHECK_REQUEST.set(null);
        extractionResponse = ExtractIntentsResponse.getDefaultInstance();
        extractionFailure = null;
        servingStatus = HealthCheckResponse.ServingStatus.SERVING;
        healthFailure = null;
    }

    private static final class StubIntentExtractionService
            extends IntentExtractionServiceGrpc.IntentExtractionServiceImplBase {

        @Override
        public void extractIntents(
                ExtractIntentsRequest request, StreamObserver<ExtractIntentsResponse> responseObserver) {
            LAST_EXTRACTION_REQUEST.set(request);
            Status failure = extractionFailure;
            if (failure != null) {
                responseObserver.onError(failure.asRuntimeException());
                return;
            }
            responseObserver.onNext(extractionResponse);
            responseObserver.onCompleted();
        }
    }

    private static final class StubHealthService extends HealthGrpc.HealthImplBase {

        @Override
        public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
            LAST_HEALTH_CHECK_REQUEST.set(request);
            Status failure = healthFailure;
            if (failure != null) {
                responseObserver.onError(failure.asRuntimeException());
                return;
            }
            responseObserver.onNext(
                    HealthCheckResponse.newBuilder().setStatus(servingStatus).build());
            responseObserver.onCompleted();
        }
    }
}
