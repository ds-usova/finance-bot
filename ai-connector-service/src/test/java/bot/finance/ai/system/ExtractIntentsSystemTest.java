package bot.finance.ai.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.common.boot.AbstractSystemTest;
import bot.finance.ai.common.fixtures.ChatCompletionFixtures;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.stubs.AuthorizedStubs;
import bot.finance.ai.common.stubs.CapturedRequestUtils;
import bot.finance.ai.common.stubs.McpLedgerStubs;
import bot.finance.ai.common.stubs.WireMockStubs;
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

    /** The grouping the scenarios file under — one of the fixture's own, so no literal is repeated. */
    private static final String GROUPING = RequestFixtures.DEFAULT_CATEGORY_GROUPINGS.getFirst();

    private static String proposalArguments() {
        return """
                {"category":"Lunch","grouping":"%s","description":"lunch","amount":"15.00",\
                "currencyCode":"EUR","merchant":"Deli Co"}"""
                .formatted(GROUPING);
    }

    private static String summarizeSpendingArguments(String from, String to) {
        return """
                {"from":"%s","to":"%s"}""".formatted(from, to);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a tokened request arrives - then the RPC answers empty and the ledger receives the "
                + "tool call under that token")
        void whenTokenedRequestArrives_thenRpcAnswersEmptyAndLedgerReceivesOneToolCallUnderToken() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-1", proposalArguments())),
                    ChatCompletionFixtures.textResponse("recorded"));

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                    .extractIntents(RequestFixtures.request());
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());

            List<LoggedRequest> toolCalls = CapturedRequestUtils.toolCallRequests();
            assertThat(toolCalls).hasSize(1);
            JsonNode arguments = CapturedRequestUtils.toolCallArguments(toolCalls.getFirst());
            assertThat(arguments.get("category").asText()).isEqualTo("Lunch");
            assertThat(arguments.get("grouping").asText()).isEqualTo(GROUPING);
            assertThat(arguments.get("merchant").asText()).isEqualTo("Deli Co");
            assertThat(arguments.get("amount").asText()).isEqualTo("15.00");
            assertThat(arguments.get("currencyCode").asText()).isEqualTo("EUR");
            assertThat(toolCalls.getFirst().getHeader("Authorization")).isEqualTo(CALLER_TOKEN);
        }

        @Test
        @DisplayName("when the provider looks up categories and then records - then both calls reach the ledger "
                + "under the request's token")
        void whenProviderLooksUpCategoriesThenRecords_thenBothToolCallsReachLedgerUnderToken() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            McpLedgerStubs.stubListCategoriesAnswering(GROUPING, List.of("Lunch"));
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall(
                            "call-list-1",
                            ChatCompletionFixtures.LedgerTool.LIST_CATEGORIES,
                            "{\"grouping\":\"" + GROUPING + "\"}")),
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-2", proposalArguments())),
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
            assertThat(lookupArguments.get("grouping").asText()).isEqualTo(GROUPING);
        }

        @Test
        @DisplayName("when the provider asks to summarize spending - then that call reaches the ledger under the "
                + "request's token")
        void whenProviderAsksToSummarizeSpending_thenThatCallReachesLedgerUnderToken() {
            String from = "2026-08-01";
            String to = RequestFixtures.DEFAULT_CURRENT_DATE;
            McpLedgerStubs.stubSummarizeSpendingAccepted(from, to);
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall(
                            "call-summarize-1",
                            ChatCompletionFixtures.LedgerTool.SUMMARIZE_SPENDING,
                            summarizeSpendingArguments(from, to))),
                    ChatCompletionFixtures.textResponse("you spent 15 euros"));

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                    .extractIntents(RequestFixtures.request());
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());

            List<LoggedRequest> summarizeSpendingCalls = CapturedRequestUtils.toolCallRequests("summarize_spending");
            assertThat(summarizeSpendingCalls).hasSize(1);
            assertThat(summarizeSpendingCalls.getFirst().getHeader("Authorization"))
                    .isEqualTo(CALLER_TOKEN);
            JsonNode arguments = CapturedRequestUtils.toolCallArguments(summarizeSpendingCalls.getFirst());
            assertThat(arguments.get("from").asText()).isEqualTo(from);
            assertThat(arguments.get("to").asText()).isEqualTo(to);
        }
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
        @DisplayName("when a request carries no authorization metadata - then it fails with UNAUTHENTICATED, the "
                + "provider never called")
        void whenRequestCarriesNoAuthorizationMetadata_thenFailsWithUnauthenticatedAndProviderNeverCalled() {
            assertThatThrownBy(() -> intentExtractionStub.extractIntents(RequestFixtures.request()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);

            assertThat(CapturedRequestUtils.chatCompletionRequests()).isEmpty();
        }
    }
}
