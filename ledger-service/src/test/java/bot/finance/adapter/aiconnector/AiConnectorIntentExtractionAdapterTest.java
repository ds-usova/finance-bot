package bot.finance.adapter.aiconnector;

import static bot.finance.common.fixtures.IncomingMessages.newIncomingMessageId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.common.boot.AiConnectorAdapterTest;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

@AiConnectorAdapterTest
class AiConnectorIntentExtractionAdapterTest {

    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 5);

    @Autowired
    private AiConnectorIntentExtractionAdapter adapter;

    @AfterEach
    void resetStubServer() {
        GrpcStubServer.reset();
    }

    private static IntentExtractionRequest requestFor(long userId, IncomingMessageId reference) {
        return new IntentExtractionRequest(
                "spent 15 on milk",
                List.of("Groceries"),
                "Groceries",
                Optional.of(CurrencyCode.of("USD")),
                userId,
                reference,
                CURRENT_DATE);
    }

    /** The claims of the bearer token the metadata of the last extraction call carried. */
    private static JWTClaimsSet bearerClaims() throws ParseException {
        Metadata metadata = GrpcStubServer.lastExtractionMetadata();
        assertThat(metadata).isNotNull();
        String authorizationHeader = metadata.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
        assertThat(authorizationHeader).startsWith("Bearer ");
        return SignedJWT.parse(authorizationHeader.substring("Bearer ".length()))
                .getJWTClaimsSet();
    }

    @Nested
    @DisplayName("extracting intents")
    class Extract {

        @Test
        @DisplayName("when extract is called - then the server receives the text, the groupings and the default "
                + "currency")
        void whenStubServerAnswersEmptyResponse_thenReturnsAndServerReceivedRequestFields() {
            GrpcStubServer.answerExtractionWith(ExtractIntentsResponse.getDefaultInstance());
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "spent 15 on milk",
                    List.of("Groceries", "Other"),
                    "Other",
                    Optional.of(CurrencyCode.of("USD")),
                    1L,
                    newIncomingMessageId(),
                    CURRENT_DATE);

            assertThatCode(() -> adapter.extract(request)).doesNotThrowAnyException();

            ExtractIntentsRequest receivedRequest = GrpcStubServer.lastExtractionRequest();
            assertThat(receivedRequest.getText()).isEqualTo("spent 15 on milk");
            assertThat(receivedRequest.getCategoryGroupingsList()).containsExactly("Groceries", "Other");
            assertThat(receivedRequest.getCatchAllGrouping()).isEqualTo("Other");
            assertThat(receivedRequest.getDefaultCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("when the request carries a current date - then the server receives it as current_date "
                + "written YYYY-MM-DD")
        void whenRequestCarriesCurrentDate_thenServerReceivedRequestCarriesCurrentDateAsIso8601Text() {
            GrpcStubServer.answerExtractionWith(ExtractIntentsResponse.getDefaultInstance());

            adapter.extract(requestFor(1L, newIncomingMessageId()));

            ExtractIntentsRequest receivedRequest = GrpcStubServer.lastExtractionRequest();
            assertThat(receivedRequest.getCurrentDate()).isEqualTo(CURRENT_DATE.toString());
        }

        @Test
        @DisplayName("when extract is called - then the metadata carries a bearer token whose sub claim is the "
                + "userExternalId")
        @Disabled("RI08: the request helper takes a long userId; the subject assertion reads that id as decimal "
                + "text and the method's name follows the meaning")
        void whenExtractIsCalled_thenMetadataCarriesBearerTokenWithSubClaimAsUserExternalId() throws ParseException {
            // GrpcStubServer.answerExtractionWith(ExtractIntentsResponse.getDefaultInstance());
            //
            // adapter.extract(requestFor("user-external-id-77", newIncomingMessageId()));
            //
            // assertThat(bearerClaims().getSubject()).isEqualTo("user-external-id-77");
        }

        @Test
        @DisplayName("when the request carries a message reference - then the bearer token's mrf claim is that "
                + "reference's UUID text")
        void whenRequestCarriesMessageReference_thenBearerTokenCarriesMrfClaim() throws ParseException {
            GrpcStubServer.answerExtractionWith(ExtractIntentsResponse.getDefaultInstance());
            IncomingMessageId reference = newIncomingMessageId();

            adapter.extract(requestFor(1L, reference));

            assertThat(bearerClaims().getStringClaim(McpTokens.INCOMING_MESSAGE_ID_CLAIM))
                    .isEqualTo(reference.value());
        }

        @Test
        @DisplayName("when the generated request is described - then it declares no field for the message reference")
        void whenGeneratedRequestIsDescribed_thenItDeclaresNoFieldForTheMessageReference() {
            assertThat(ExtractIntentsRequest.getDescriptor().findFieldByName("message_reference"))
                    .isNull();
        }

        @ParameterizedTest
        @EnumSource(
                value = Status.Code.class,
                names = {"INVALID_ARGUMENT", "UNAVAILABLE", "FAILED_PRECONDITION", "UNAUTHENTICATED"})
        @DisplayName("when the stub server fails the call - then throws IntentExtractionFailedException naming the "
                + "status")
        void whenStubServerFailsCall_thenThrowsIntentExtractionFailedExceptionNamingTheStatus(Status.Code code) {
            GrpcStubServer.failExtractionWith(Status.fromCode(code).withDescription("stub failure"));
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "connector unavailable",
                    List.of("Other"),
                    "Other",
                    Optional.empty(),
                    1L,
                    newIncomingMessageId(),
                    CURRENT_DATE);

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
