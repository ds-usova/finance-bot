package bot.finance.ai.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.common.AbstractSystemTest;
import bot.finance.ai.common.AuthorizedStubs;
import bot.finance.ai.common.CapturedRequestUtils;
import bot.finance.ai.common.ChatCompletionFixtures;
import bot.finance.ai.common.McpLedgerStubs;
import bot.finance.ai.common.RequestFixtures;
import bot.finance.ai.common.WireMockStubs;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Entered over the real Netty channel {@link AbstractSystemTest} binds, so a passing happy path also proves the
 * server binds and serves — the in-process transport {@code @GrpcAdapterTest} uses would have hidden that.
 */
class ExtractIntentsSystemTest extends AbstractSystemTest {

    private static final String CALLER_TOKEN = "Bearer opaque-caller-token";

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a tokened request with a text and the default known categories arrives - then the RPC "
                + "answers an empty response, and the ledger received one tool call carrying the provider's "
                + "arguments under the request's own token")
        void whenTokenedRequestArrives_thenRpcAnswersEmptyResponseAndLedgerReceivesOneToolCallUnderToken() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall(
                                    "call-1",
                                    """
                            {"category":"Lunch","parentCategory":"Food","description":"lunch","amount":"15.00",\
                            "currencyCode":"EUR","merchant":"Deli Co"}""")),
                    ChatCompletionFixtures.textResponse("recorded"));

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                    .extractIntents(RequestFixtures.request());
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());

            List<LoggedRequest> toolCalls = CapturedRequestUtils.toolCallRequests();
            assertThat(toolCalls).hasSize(1);
            JsonNode arguments = CapturedRequestUtils.toolCallArguments(toolCalls.getFirst());
            assertThat(arguments.get("category").asText()).isEqualTo("Lunch");
            assertThat(arguments.get("parentCategory").asText()).isEqualTo("Food");
            assertThat(arguments.get("merchant").asText()).isEqualTo("Deli Co");
            assertThat(arguments.get("amount").asText()).isEqualTo("15.00");
            assertThat(arguments.get("currencyCode").asText()).isEqualTo("EUR");
            assertThat(toolCalls.getFirst().getHeader("Authorization")).isEqualTo(CALLER_TOKEN);
        }

        @Test
        @DisplayName("when a tokened request carrying grouping names and a catch-all arrives - then the RPC "
                + "answers an empty response, the ledger received both calls under the request's own token, and "
                + "the lookup carried the grouping name the provider asked for")
        void whenTokenedRequestArrives_thenRpcAnswersEmptyResponseAndLedgerReceivesBothToolCallsUnderToken() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            McpLedgerStubs.stubListCategoriesAnswering("Food", List.of("Lunch"));
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(listCategoriesToolCall("call-list-1", "Food")),
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall(
                                    "call-2",
                                    """
                            {"category":"Lunch","parentCategory":"Food","description":"lunch","amount":"15.00",\
                            "currencyCode":"EUR","merchant":"Deli Co"}""")),
                    ChatCompletionFixtures.textResponse("recorded"));

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                    .extractIntents(RequestFixtures.request());
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());

            List<LoggedRequest> listCategoriesCalls = CapturedRequestUtils.toolCallRequests("list_categories");
            List<LoggedRequest> createExpenseProposalCalls = CapturedRequestUtils.toolCallRequests();
            assertThat(listCategoriesCalls).hasSize(1);
            assertThat(createExpenseProposalCalls).hasSize(1);
            assertThat(listCategoriesCalls.getFirst().getHeader("Authorization"))
                    .isEqualTo(CALLER_TOKEN);
            assertThat(createExpenseProposalCalls.getFirst().getHeader("Authorization"))
                    .isEqualTo(CALLER_TOKEN);

            JsonNode lookupArguments = CapturedRequestUtils.toolCallArguments(listCategoriesCalls.getFirst());
            assertThat(lookupArguments.get("parentCategory").asText()).isEqualTo("Food");
        }
    }

    /**
     * A {@code list_categories} tool-call entry the provider might send, its argument the sole
     * {@code parentCategory} the tool declares.
     */
    private static String listCategoriesToolCall(String id, String parentCategory) {
        return """
                {
                  "id": "%s",
                  "type": "function",
                  "function": {
                    "name": "list_categories",
                    "arguments": "{\\"parentCategory\\":\\"%s\\"}"
                  }
                }
                """
                .formatted(id, parentCategory);
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when a tokened request arrives and the provider responds with a server error - then the RPC "
                + "fails with UNAVAILABLE")
        void whenProviderRespondsServerError_thenFailsWithUnavailable() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionServerError();

            assertThatThrownBy(() -> AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                            .extractIntents(RequestFixtures.request()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAVAILABLE);
        }

        @Test
        @DisplayName("when a request carrying no authorization metadata arrives - then it fails with UNAUTHENTICATED "
                + "and the provider is never called")
        void whenRequestCarriesNoAuthorizationMetadata_thenFailsWithUnauthenticatedAndProviderNeverCalled() {
            assertThatThrownBy(() -> intentExtractionStub.extractIntents(RequestFixtures.request()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);

            assertThat(CapturedRequestUtils.chatCompletionRequests()).isEmpty();
        }
    }
}
