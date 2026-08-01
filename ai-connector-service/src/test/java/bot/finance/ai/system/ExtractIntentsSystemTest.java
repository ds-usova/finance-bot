package bot.finance.ai.system;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
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

            ExtractIntentsResponse response = intentExtractionStub.extractIntents(request);
            log.info("response: {}", response);

            List<LoggedRequest> requests = WireMockSupport.SERVER.findAll(
                    postRequestedFor(urlPathEqualTo(WireMockStubs.CHAT_COMPLETIONS_PATH)));
            assertThat(requests).hasSize(1);
            String requestBody = requests.getFirst().getBodyAsString();
            log.info("request body received by the provider: {}", requestBody);
            RequestFixtures.DEFAULT_KNOWN_CATEGORIES.forEach(
                    category -> assertThat(requestBody).contains(category.getName()));
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
                    StatusRuntimeException.class, () -> intentExtractionStub.extractIntents(request));

            log.info("status: {}", exception.getStatus());
            assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        }

    }

}
