package bot.finance.ai.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.common.AuthorizedStubs;
import bot.finance.ai.common.GrpcAdapterTest;
import bot.finance.ai.common.RequestFixtures;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GrpcAdapterTest
@ImportGrpcClients(types = HealthGrpc.HealthBlockingStub.class)
class CallerTokenInterceptorTest {

    @Autowired
    private IntentExtractionServiceBlockingStub intentExtractionStub;

    @Autowired
    private HealthGrpc.HealthBlockingStub healthStub;

    @MockitoBean
    private ExtractIntentsPort extractIntentsPort;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName(
                "when the stub carries an authorization header - then the port is called with the caller token available in its context")
        void whenStubCarriesAuthorizationHeader_thenPortSeesCallerTokenInContext() {
            AtomicReference<Optional<String>> capturedToken = new AtomicReference<>();
            doAnswer(invocation -> {
                        capturedToken.set(CallerTokenContext.callerToken());
                        return null;
                    })
                    .when(extractIntentsPort)
                    .extractIntents(any());

            AuthorizedStubs.withCallerToken(intentExtractionStub, "Bearer abc")
                    .extractIntents(RequestFixtures.request());

            verify(extractIntentsPort).extractIntents(any());
            assertThat(capturedToken.get()).contains("Bearer abc");
        }
    }

    @Nested
    @DisplayName("Error mapping")
    class ErrorMapping {

        @Test
        @DisplayName(
                "when the stub carries no authorization header - then the RPC fails with UNAUTHENTICATED and the port is never called")
        void whenStubCarriesNoAuthorizationHeader_thenFailsWithUnauthenticatedAndPortNeverCalled() {
            assertThatThrownBy(() -> intentExtractionStub.extractIntents(RequestFixtures.request()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);
            verify(extractIntentsPort, never()).extractIntents(any());
        }

        @Test
        @DisplayName(
                "when the health service is checked with no authorization header - then it succeeds, since the refusal is scoped to IntentExtractionService")
        void whenHealthServiceCheckedWithNoAuthorizationHeader_thenSucceeds() {
            HealthCheckResponse response = healthStub.check(HealthCheckRequest.getDefaultInstance());

            assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        }
    }
}
