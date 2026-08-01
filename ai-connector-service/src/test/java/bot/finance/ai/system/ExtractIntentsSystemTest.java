package bot.finance.ai.system;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub;
import bot.finance.ai.common.AbstractSystemTest;
import bot.finance.ai.common.ChatCompletionFixtures;
import bot.finance.ai.common.McpLedgerStubs;
import bot.finance.ai.common.RequestFixtures;
import bot.finance.ai.common.WireMockStubs;
import bot.finance.ai.common.WireMockSupport;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Entered over the real Netty channel {@link AbstractSystemTest} binds, so a passing happy path also proves the
 * server binds and serves — the in-process transport {@code @GrpcAdapterTest} uses would have hidden that.
 */
class ExtractIntentsSystemTest extends AbstractSystemTest {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String CALLER_TOKEN = "caller-token-1";

    private IntentExtractionServiceBlockingStub authenticatedStub() {
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, "Bearer " + CALLER_TOKEN);
        return intentExtractionStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when text names no category - then the model saw the full closed set as rendered labels")
        void whenTextNamesNoCategory_thenExpenseIsFiledUnderTheMatchingKnownCategory() {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson(
                    ChatCompletionFixtures.intentEntry()
                            .target("expense")
                            .operation("create")
                            .categoryName("Food")
                            .amount("15.00")
                            .currency("EUR")
                            .description("lunch")
                            .build()));

            ExtractIntentsRequest request = RequestFixtures.request();

            authenticatedStub().extractIntents(request);

            List<LoggedRequest> requests = WireMockSupport.SERVER.findAll(
                    postRequestedFor(urlPathEqualTo(WireMockStubs.CHAT_COMPLETIONS_PATH)));
            assertThat(requests).hasSize(1);
            String requestBody = requests.getFirst().getBodyAsString();
            log.info("request body received by the provider: {}", requestBody);
            RequestFixtures.DEFAULT_KNOWN_CATEGORIES.forEach(category -> assertThat(requestBody)
                    .contains(category.getParentName() + " > " + category.getName()));
        }

        @Test
        @DisplayName("when the provider answers one CREATE expense and the stubbed ledger accepts the tool call - "
                + "then it answers an empty response, and the ledger received one create_expense_proposal call "
                + "carrying Travel, Insurance, the description and the amount, under the request's own token")
        void whenProviderAnswersOneCreateExpenseAndLedgerAccepts_thenLedgerReceivesProposalUnderRequestToken() {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson(
                    ChatCompletionFixtures.intentEntry()
                            .target("expense")
                            .operation("create")
                            .categoryName("Travel")
                            .amount("45.00")
                            .currency("EUR")
                            .description("flight")
                            .build()));
            McpLedgerStubs.stubCreateExpenseProposalAccepted();

            ExtractIntentsResponse response = authenticatedStub().extractIntents(RequestFixtures.request());
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());

            List<LoggedRequest> ledgerRequests = WireMockSupport.SERVER.findAll(
                    postRequestedFor(urlPathEqualTo(McpLedgerStubs.MCP_PATH)));
            List<LoggedRequest> toolCallRequests = ledgerRequests.stream()
                    .filter(request -> request.getBodyAsString().contains("create_expense_proposal"))
                    .toList();
            log.info("ledger tool-call requests: {}", toolCallRequests);
            assertThat(toolCallRequests).hasSize(1);
            LoggedRequest toolCallRequest = toolCallRequests.getFirst();
            String toolCallBody = toolCallRequest.getBodyAsString();
            assertThat(toolCallBody).contains("\"category\":\"Travel\"");
            assertThat(toolCallBody).contains("\"parentCategory\":\"Insurance\"");
            assertThat(toolCallBody).contains("\"description\":\"flight\"");
            assertThat(toolCallBody).contains("\"amountMinorUnits\":4500");
            assertThat(toolCallRequest.getHeader("Authorization")).isEqualTo("Bearer " + CALLER_TOKEN);
        }

    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the provider responds with a server error - then fails with status UNAVAILABLE")
        void whenProviderRespondsWithServerError_thenFailsWithStatusUnavailable() {
            WireMockStubs.stubChatCompletionServerError();

            ExtractIntentsRequest request = RequestFixtures.request();

            StatusRuntimeException exception = catchThrowableOfType(
                    StatusRuntimeException.class, () -> authenticatedStub().extractIntents(request));

            log.info("status: {}", exception.getStatus());
            assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        }

        @Test
        @DisplayName("when the provider answers one CREATE expense and the stubbed ledger refuses the proposal - "
                + "then it fails with status FAILED_PRECONDITION")
        void whenLedgerRefusesProposal_thenFailsWithStatusFailedPrecondition() {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson(
                    ChatCompletionFixtures.intentEntry()
                            .target("expense")
                            .operation("create")
                            .categoryName("Travel")
                            .amount("45.00")
                            .currency("EUR")
                            .description("flight")
                            .build()));
            McpLedgerStubs.stubCreateExpenseProposalRefused();

            ExtractIntentsRequest request = RequestFixtures.request();

            assertThatThrownBy(() -> authenticatedStub().extractIntents(request))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.FAILED_PRECONDITION);
        }

        @Test
        @DisplayName("when a request arrives with no authorization metadata - then it fails with status "
                + "UNAUTHENTICATED and the provider is never called")
        void whenRequestCarriesNoAuthorizationMetadata_thenFailsWithUnauthenticatedAndProviderNeverCalled() {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson(
                    ChatCompletionFixtures.intentEntry()
                            .target("expense")
                            .operation("create")
                            .categoryName("Food")
                            .amount("15.00")
                            .currency("EUR")
                            .description("lunch")
                            .build()));

            ExtractIntentsRequest request = RequestFixtures.request();

            assertThatThrownBy(() -> intentExtractionStub.extractIntents(request))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);

            List<LoggedRequest> requests = WireMockSupport.SERVER.findAll(
                    postRequestedFor(urlPathEqualTo(WireMockStubs.CHAT_COMPLETIONS_PATH)));
            assertThat(requests).isEmpty();
        }

    }

}
