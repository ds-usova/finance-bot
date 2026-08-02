package bot.finance.ai.system;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1", """
                            {"category":"Lunch","description":"lunch","amountMinorUnits":1500,\
                            "currencyCode":"EUR","merchant":"Deli Co"}""")),
                    ChatCompletionFixtures.textResponse("recorded"));

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                    .extractIntents(RequestFixtures.request());
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());

            List<LoggedRequest> toolCalls = CapturedRequestUtils.toolCallRequests();
            assertThat(toolCalls).hasSize(1);
            JsonNode arguments = CapturedRequestUtils.toolCallArguments(toolCalls.get(0));
            assertThat(arguments.get("category").asText()).isEqualTo("Lunch");
            assertThat(arguments.get("merchant").asText()).isEqualTo("Deli Co");
            assertThat(arguments.get("amountMinorUnits").asLong()).isEqualTo(1500L);
            assertThat(arguments.get("currencyCode").asText()).isEqualTo("EUR");
            assertThat(toolCalls.get(0).getHeader("Authorization")).isEqualTo(CALLER_TOKEN);
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
