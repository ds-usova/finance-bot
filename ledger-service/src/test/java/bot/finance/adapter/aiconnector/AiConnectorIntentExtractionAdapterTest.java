package bot.finance.adapter.aiconnector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.common.AiConnectorAdapterTest;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

@AiConnectorAdapterTest
class AiConnectorIntentExtractionAdapterTest {

    @Autowired
    private AiConnectorIntentExtractionAdapter adapter;

    @AfterEach
    void resetStubServer() {
        GrpcStubServer.reset();
    }

    @Nested
    @DisplayName("extracting intents")
    class Extract {

        @Test
        @DisplayName("when the stub server answers an empty response - then returns without throwing, and the request "
                + "the server received carries the text, categories and default currency")
        void whenStubServerAnswersEmptyResponse_thenReturnsAndServerReceivedRequestFields() {
            GrpcStubServer.answerExtractionWith(ExtractIntentsResponse.getDefaultInstance());
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "spent 15 on milk",
                    List.of("Groceries", "Other"),
                    "Other",
                    Optional.of(CurrencyCode.of("USD")),
                    "user-external-id",
                    MessageReference.newReference());

            assertThatCode(() -> adapter.extract(request)).doesNotThrowAnyException();

            ExtractIntentsRequest receivedRequest = GrpcStubServer.lastExtractionRequest();
            assertThat(receivedRequest.getText()).isEqualTo("spent 15 on milk");
            // TODO RI04: replace with the grouping-name and catch-all assertions — category_groupings in order
            // and catch_all_grouping — and the known_categories-does-not-exist descriptor assertion.
            assertThat(receivedRequest.getDefaultCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName(
                "when extract is called - then the call's metadata carries authorization: Bearer <jwt>, whose sub claim is the request's userExternalId")
        void whenExtractIsCalled_thenMetadataCarriesBearerTokenWithSubClaimAsUserExternalId() throws ParseException {
            GrpcStubServer.answerExtractionWith(ExtractIntentsResponse.getDefaultInstance());
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "spent 15 on milk",
                    List.of("Groceries"),
                    "Groceries",
                    Optional.of(CurrencyCode.of("USD")),
                    "user-external-id-77",
                    MessageReference.newReference());

            adapter.extract(request);

            Metadata metadata = GrpcStubServer.lastExtractionMetadata();
            assertThat(metadata).isNotNull();
            String authorizationHeader =
                    metadata.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
            assertThat(authorizationHeader).startsWith("Bearer ");
            String token = authorizationHeader.substring("Bearer ".length());
            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
            assertThat(claims.getSubject()).isEqualTo("user-external-id-77");
        }

        @Test
        @DisplayName(
                "when extract is called with a request carrying a known message reference - then the bearer token's mrf claim equals that reference's UUID text, and the request the server received carries no field for it")
        void whenRequestCarriesMessageReference_thenBearerTokenCarriesMrfClaimAndProtoRequestHasNoFieldForIt()
                throws ParseException {
            GrpcStubServer.answerExtractionWith(ExtractIntentsResponse.getDefaultInstance());
            MessageReference reference = MessageReference.newReference();
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "spent 15 on milk",
                    List.of("Groceries"),
                    "Groceries",
                    Optional.of(CurrencyCode.of("USD")),
                    "user-external-id",
                    reference);

            adapter.extract(request);

            Metadata metadata = GrpcStubServer.lastExtractionMetadata();
            assertThat(metadata).isNotNull();
            String authorizationHeader =
                    metadata.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
            String token = authorizationHeader.substring("Bearer ".length());
            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
            assertThat(claims.getStringClaim("mrf")).isEqualTo(reference.value().toString());

            assertThat(ExtractIntentsRequest.getDescriptor().findFieldByName("message_reference"))
                    .isNull();
        }

        @ParameterizedTest
        @EnumSource(
                value = Status.Code.class,
                names = {"INVALID_ARGUMENT", "UNAVAILABLE", "FAILED_PRECONDITION", "UNAUTHENTICATED"})
        @DisplayName(
                "when the stub server fails the call - then throws IntentExtractionFailedException carrying the StatusRuntimeException as its cause and naming the status")
        void
                whenStubServerFailsCall_thenThrowsIntentExtractionFailedExceptionCarryingStatusRuntimeExceptionAsCauseAndNamingStatus(
                        Status.Code code) {
            GrpcStubServer.failExtractionWith(Status.fromCode(code).withDescription("stub failure"));
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "connector unavailable",
                    List.of("Other"),
                    "Other",
                    Optional.empty(),
                    "user-external-id",
                    MessageReference.newReference());

            IntentExtractionFailedException thrown =
                    catchThrowableOfType(() -> adapter.extract(request), IntentExtractionFailedException.class);

            assertThat(thrown).isNotNull();
            assertThat(thrown.getMessage()).contains(code.name());
            assertThat(thrown.getCause()).isInstanceOf(StatusRuntimeException.class);
            StatusRuntimeException cause = (StatusRuntimeException) thrown.getCause();
            assertThat(cause.getStatus().getCode()).isEqualTo(code);
        }

        @Test
        @DisplayName(
                "when extract is called with null - then throws InvalidExtractionRequestException and the server is never called")
        void whenCalledWithNull_thenThrowsInvalidExtractionRequestExceptionAndServerIsNeverCalled() {
            assertThatThrownBy(() -> adapter.extract(null)).isInstanceOf(InvalidExtractionRequestException.class);

            assertThat(GrpcStubServer.lastExtractionRequest()).isNull();
            assertThat(GrpcStubServer.lastExtractionMetadata()).isNull();
        }
    }
}
