package bot.finance.common.containers;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
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
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class GrpcStubServer {

    public static final Server SERVER;

    private static final HttpClient CALLBACK_CLIENT = HttpClient.newHttpClient();

    private static final AtomicReference<ExtractIntentsRequest> LAST_EXTRACTION_REQUEST = new AtomicReference<>();
    private static final AtomicReference<Metadata> LAST_EXTRACTION_METADATA = new AtomicReference<>();
    private static final AtomicReference<HealthCheckRequest> LAST_HEALTH_CHECK_REQUEST = new AtomicReference<>();
    private static final AtomicReference<String> CALLBACK_BASE_URL = new AtomicReference<>();
    private static final AtomicReference<List<String>> CALLBACK_REQUEST_BODIES = new AtomicReference<>(List.of());
    private static final List<String> CALLBACK_RESPONSE_BODIES = new CopyOnWriteArrayList<>();

    private static volatile ExtractIntentsResponse extractionResponse = ExtractIntentsResponse.getDefaultInstance();
    private static volatile Status extractionFailure;
    private static volatile HealthCheckResponse.ServingStatus servingStatus = HealthCheckResponse.ServingStatus.SERVING;
    private static volatile Status healthFailure;

    static {
        try {
            SERVER = ServerBuilder.forPort(0)
                    .addService(ServerInterceptors.intercept(
                            new StubIntentExtractionService(), new ExtractionMetadataInterceptor()))
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

    public static Metadata lastExtractionMetadata() {
        return LAST_EXTRACTION_METADATA.get();
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
        LAST_EXTRACTION_METADATA.set(null);
        LAST_HEALTH_CHECK_REQUEST.set(null);
        extractionResponse = ExtractIntentsResponse.getDefaultInstance();
        extractionFailure = null;
        servingStatus = HealthCheckResponse.ServingStatus.SERVING;
        healthFailure = null;
        CALLBACK_BASE_URL.set(null);
        CALLBACK_REQUEST_BODIES.set(List.of());
        CALLBACK_RESPONSE_BODIES.clear();
    }

    /**
     * Arms the callback mode: the next {@code extractIntents} call posts each of {@code toolCallRequestBodies} to
     * {@code <baseUrl>/mcp} in turn before answering, forwarding the {@code authorization} header it received
     * verbatim. This is the only way a system test can reach the {@code RECORDED} outcome, since the reference is
     * minted inside the use case and no test can seed a proposal row under it beforehand. A turn that makes several
     * tool calls is armed by naming them in the order the model would make them.
     */
    public static void armMcpCallbacks(String baseUrl, String... toolCallRequestBodies) {
        CALLBACK_BASE_URL.set(baseUrl);
        CALLBACK_REQUEST_BODIES.set(List.of(toolCallRequestBodies));
        CALLBACK_RESPONSE_BODIES.clear();
    }

    /** What {@code /mcp} answered each armed call, in the order the calls were made. */
    public static List<String> mcpCallbackResponses() {
        return List.copyOf(CALLBACK_RESPONSE_BODIES);
    }

    private static final class ExtractionMetadataInterceptor implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            LAST_EXTRACTION_METADATA.set(headers);
            return Contexts.interceptCall(Context.current(), call, headers, next);
        }
    }

    private static final class StubIntentExtractionService
            extends IntentExtractionServiceGrpc.IntentExtractionServiceImplBase {

        @Override
        public void extractIntents(
                ExtractIntentsRequest request, StreamObserver<ExtractIntentsResponse> responseObserver) {
            LAST_EXTRACTION_REQUEST.set(request);
            String baseUrl = CALLBACK_BASE_URL.get();
            if (baseUrl != null) {
                callBackIntoMcp(baseUrl);
            }
            Status failure = extractionFailure;
            if (failure != null) {
                responseObserver.onError(failure.asRuntimeException());
                return;
            }
            responseObserver.onNext(extractionResponse);
            responseObserver.onCompleted();
        }

        private void callBackIntoMcp(String baseUrl) {
            Metadata metadata = LAST_EXTRACTION_METADATA.get();
            String authorization = metadata == null
                    ? null
                    : metadata.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
            try {
                for (String body : CALLBACK_REQUEST_BODIES.get()) {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/mcp"))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json, text/event-stream")
                            .header("Authorization", authorization == null ? "" : authorization)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
                    HttpResponse<String> response = CALLBACK_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                    CALLBACK_RESPONSE_BODIES.add(response.body());
                }
            } catch (IOException e) {
                throw new UncheckedIOException("failed to call back into /mcp from the stub connector", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while calling back into /mcp from the stub connector", e);
            }
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
