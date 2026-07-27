package bot.finance.ai.system;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.Intent;
import bot.finance.ai.adapter.grpc.v1.Operation;
import bot.finance.ai.common.AbstractSystemTest;
import bot.finance.ai.common.ChatCompletionFixtures;
import bot.finance.ai.common.RequestFixtures;
import bot.finance.ai.common.WireMockStubs;
import bot.finance.ai.common.WireMockSupport;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Entered over the real Netty channel {@link AbstractSystemTest} binds, so a passing happy path also proves the
 * server binds and serves — the in-process transport {@code @GrpcAdapterTest} uses would have hidden that.
 */
class ExtractIntentsSystemTest extends AbstractSystemTest {

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a simple expense is stated - then returns one CREATE expense intent")
        void whenTextNamesASimpleExpense_thenReturnsOneCreateExpenseIntent() {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson(
                    ChatCompletionFixtures.intentEntry()
                            .target("expense")
                            .operation("create")
                            .categoryName("Lunch")
                            .amount("15.00")
                            .currency("EUR")
                            .description("lunch")
                            .build()));

            ExtractIntentsRequest request = RequestFixtures.request(
                    "spent 15 euros on lunch", List.of("Lunch", "Other"));

            ExtractIntentsResponse response = intentExtractionStub.extractIntents(request);
            log.info("response: {}", response);

            assertThat(response.getIntentsList()).hasSize(1);
            Intent intent = response.getIntents(0);
            assertThat(intent.getOperation()).isEqualTo(Operation.OPERATION_CREATE);
            assertThat(intent.hasExpense()).isTrue();
            assertThat(intent.getExpense().getAmount().getMinorUnits()).isEqualTo(1500L);
            assertThat(intent.getExpense().getAmount().getCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when text creates a category and an expense in it - then returns both intents in the user's order")
        void whenTextCreatesACategoryAndAnExpenseInIt_thenReturnsBothIntentsInOrder() {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson(
                    ChatCompletionFixtures.intentEntry()
                            .target("category")
                            .operation("create")
                            .categoryName("Travel")
                            .build(),
                    ChatCompletionFixtures.intentEntry()
                            .target("expense")
                            .operation("create")
                            .categoryName("Travel")
                            .amount("50.00")
                            .currency("EUR")
                            .description("taxi")
                            .build()));

            ExtractIntentsRequest request = RequestFixtures.request(
                    "create a Travel category and put 50 euros of taxi in it");

            ExtractIntentsResponse response = intentExtractionStub.extractIntents(request);
            log.info("response: {}", response);

            assertThat(response.getIntentsList()).hasSize(2);
            Intent first = response.getIntents(0);
            Intent second = response.getIntents(1);
            assertThat(first.getOperation()).isEqualTo(Operation.OPERATION_CREATE);
            assertThat(first.hasCategory()).isTrue();
            assertThat(first.getCategory().getName()).isEqualTo("Travel");
            assertThat(second.getOperation()).isEqualTo(Operation.OPERATION_CREATE);
            assertThat(second.hasExpense()).isTrue();
            assertThat(second.getExpense().getAmount().getMinorUnits()).isEqualTo(5000L);
            assertThat(second.getExpense().getAmount().getCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when text names no category - then the expense is filed under the matching known category, and the model saw the full closed set")
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

            ExtractIntentsRequest request = RequestFixtures.request("spent 15 euros on lunch");

            ExtractIntentsResponse response = intentExtractionStub.extractIntents(request);
            log.info("response: {}", response);

            assertThat(response.getIntentsList()).hasSize(1);
            Intent intent = response.getIntents(0);
            assertThat(intent.hasExpense()).isTrue();
            assertThat(intent.getExpense().getCategoryName()).isEqualTo("Food");

            List<LoggedRequest> requests =
                    WireMockSupport.SERVER.findAll(postRequestedFor(urlPathEqualTo(CHAT_COMPLETIONS_PATH)));
            assertThat(requests).hasSize(1);
            String requestBody = requests.get(0).getBodyAsString();
            log.info("request body received by the provider: {}", requestBody);
            assertThat(requestBody).contains("Food").contains("Travel").contains("Other");
        }

        @Test
        @DisplayName("when the provider extracts nothing actionable - then returns one UNKNOWN intent with a reason")
        void whenProviderExtractsNothingActionable_thenReturnsOneUnknownIntent() {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson());

            ExtractIntentsRequest request = RequestFixtures.request("what is the weather");

            ExtractIntentsResponse response = intentExtractionStub.extractIntents(request);
            log.info("response: {}", response);

            assertThat(response.getIntentsList()).hasSize(1);
            Intent intent = response.getIntents(0);
            assertThat(intent.getOperation()).isEqualTo(Operation.OPERATION_UNKNOWN);
            assertThat(intent.getReason()).isNotBlank();
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
                    () -> intentExtractionStub.extractIntents(request), StatusRuntimeException.class);

            log.info("status: {}", exception.getStatus());
            assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        }

    }

}
