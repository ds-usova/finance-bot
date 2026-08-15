package bot.finance.ai.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub;
import bot.finance.ai.adapter.security.CallerTokenVerifier;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.common.boot.GrpcAdapterTest;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.stubs.AuthorizedStubs;
import bot.finance.ai.domain.exception.CallerNotIdentifiedException;
import bot.finance.ai.domain.exception.CallerVerificationUnavailableException;
import bot.finance.ai.domain.value.MessageIdentity;
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

        @Test
        @DisplayName("when memory is disabled and the token would not verify - then the port is called with no "
                + "identity in context")
        void whenMemoryDisabledAndTokenWouldNotVerify_thenPortCalledAndMessageIdentityAbsent() {
            AtomicReference<Optional<MessageIdentity>> capturedIdentity = new AtomicReference<>();
            doAnswer(invocation -> {
                        capturedIdentity.set(CallerTokenContext.messageIdentity());
                        return null;
                    })
                    .when(extractIntentsPort)
                    .extractIntents(any());

            AuthorizedStubs.withCallerToken(intentExtractionStub, "Bearer not-a-valid-jwt")
                    .extractIntents(RequestFixtures.request());

            verify(extractIntentsPort).extractIntents(any());
            assertThat(capturedIdentity.get()).isEmpty();
        }

        @Nested
        @DisplayName("with the caller identified")
        class CallerIdentified {

            @MockitoBean
            private CallerTokenVerifier callerTokenVerifier;

            @Test
            @DisplayName("when the verifier identifies the caller - then it is asked with the header's value and "
                    + "the identity joins the context")
            void whenVerifierIdentifiesCaller_thenVerifierAskedAndContextHoldsIdentityBesideToken() {
                MessageIdentity identity = new MessageIdentity(42L, "incoming-message-1");
                when(callerTokenVerifier.verify("Bearer abc")).thenReturn(identity);

                AtomicReference<Optional<String>> capturedToken = new AtomicReference<>();
                AtomicReference<Optional<MessageIdentity>> capturedIdentity = new AtomicReference<>();
                doAnswer(invocation -> {
                            capturedToken.set(CallerTokenContext.callerToken());
                            capturedIdentity.set(CallerTokenContext.messageIdentity());
                            return null;
                        })
                        .when(extractIntentsPort)
                        .extractIntents(any());

                AuthorizedStubs.withCallerToken(intentExtractionStub, "Bearer abc")
                        .extractIntents(RequestFixtures.request());

                verify(callerTokenVerifier).verify("Bearer abc");
                verify(extractIntentsPort).extractIntents(any());
                assertThat(capturedToken.get()).contains("Bearer abc");
                assertThat(capturedIdentity.get()).contains(identity);
            }
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

        @Nested
        @DisplayName("with the verifier mocked")
        class VerifierMocked {

            @MockitoBean
            private CallerTokenVerifier callerTokenVerifier;

            @Test
            @DisplayName("when the verifier throws CallerNotIdentifiedException - then it fails with "
                    + "UNAUTHENTICATED and the port is never called")
            void whenVerifierThrowsCallerNotIdentifiedException_thenFailsWithUnauthenticatedAndPortNeverCalled() {
                doThrow(new CallerNotIdentifiedException("token carries no usable claims"))
                        .when(callerTokenVerifier)
                        .verify(any());

                assertThatThrownBy(() -> AuthorizedStubs.withCallerToken(intentExtractionStub, "Bearer bad")
                                .extractIntents(RequestFixtures.request()))
                        .isInstanceOf(StatusRuntimeException.class)
                        .extracting(
                                ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.UNAUTHENTICATED);
                verify(extractIntentsPort, never()).extractIntents(any());
            }

            @Test
            @DisplayName("when the verifier throws CallerVerificationUnavailableException - then it fails with "
                    + "UNAVAILABLE, port never called")
            void whenVerifierThrowsCallerVerificationUnavailableException_thenFailsWithUnavailableAndPortNeverCalled() {
                doThrow(new CallerVerificationUnavailableException("key set unreachable"))
                        .when(callerTokenVerifier)
                        .verify(any());

                assertThatThrownBy(() -> AuthorizedStubs.withCallerToken(intentExtractionStub, "Bearer bad")
                                .extractIntents(RequestFixtures.request()))
                        .isInstanceOf(StatusRuntimeException.class)
                        .extracting(
                                ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.UNAVAILABLE);
                verify(extractIntentsPort, never()).extractIntents(any());
            }

            /** The refusal is scoped to {@code IntentExtractionService}, so the health service answers unauthenticated. */
            @Test
            @DisplayName("when the health service is checked with no authorization header - then it succeeds and "
                    + "the verifier is never called")
            void whenHealthServiceCheckedWithNoAuthorizationHeader_thenSucceedsAndVerifierNeverCalled() {
                HealthCheckResponse response = healthStub.check(HealthCheckRequest.getDefaultInstance());

                assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
                verify(callerTokenVerifier, never()).verify(any());
            }
        }
    }
}
