package bot.finance.ai.system;

import static bot.finance.ai.common.PrometheusScrapeUtils.scrape;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.GroupingCategories;
import bot.finance.ai.adapter.metrics.MeterName;
import bot.finance.ai.common.boot.AbstractClassifierSystemTest;
import bot.finance.ai.common.containers.LedgerTurnServer.RecordedCall;
import bot.finance.ai.common.fixtures.ChatCompletionFixtures;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.fixtures.SystemOneFixtures;
import bot.finance.ai.common.fixtures.SystemOneFixtures.QuestionAnswer;
import bot.finance.ai.common.stubs.AuthorizedStubs;
import bot.finance.ai.common.stubs.CapturedRequestUtils;
import bot.finance.ai.common.stubs.ClassifierStubs;
import bot.finance.ai.common.stubs.LedgerTurnStubs;
import bot.finance.ai.common.stubs.WireMockStubs;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalRequest;
import com.fasterxml.jackson.databind.JsonNode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Entered over the real Netty channel with the classifier mode on: the provider reads the message out, the
 * classifier answers over the stub server, and the fake ledger turn server receives the filings.
 */
class ClassifierTurnSystemTest extends AbstractClassifierSystemTest {

    private static final String CALLER_TOKEN = "Bearer opaque-caller-token";
    private static final String CAFES = "Dining / Cafés";
    private static final String RESTAURANTS = "Dining / Restaurants";
    private static final String TAXIS = "Transportation / Taxis";

    private static final List<GroupingCategories> CATALOGUE = List.of(
            GroupingCategories.newBuilder()
                    .setGrouping("Dining")
                    .addCategories("Cafés")
                    .addCategories("Restaurants")
                    .build(),
            GroupingCategories.newBuilder()
                    .setGrouping("Transportation")
                    .addCategories("Taxis")
                    .build());

    private static double sampleOrZero(String body, String metric, String... labels) {
        for (String line : body.split("\n")) {
            if (!line.startsWith(metric + "{")) {
                continue;
            }
            String renderedLabels = line.substring(0, line.indexOf('}'));
            if (Arrays.stream(labels).allMatch(renderedLabels::contains)) {
                return Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1));
            }
        }
        return 0.0;
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when the classifier answers confidently - then the call is acted on, the ledger receives one "
                + "filing under the token, the provider got no tools and the turn counts")
        void whenClassifierAnswersConfidently_thenFilingReachesLedgerUnderTokenAndTurnCounts() {
            String turns = MeterName.TURNS.meterName();
            double before = sampleOrZero(scrape(actuatorPort), turns, "mode=\"classifier\"", "outcome=\"acted_on\"");
            WireMockStubs.stubChatCompletion(
                    ChatCompletionFixtures.readOut(
                            """
                    {"expenses": [
                      {"description": "coffee", "merchant": null, "amount": "4.50", "currency": "EUR"}
                    ], "periods": []}"""));
            ClassifierStubs.stubAnswered(SystemOneFixtures.answer(
                    new QuestionAnswer("0", CAFES, 0.95, Map.of(CAFES, 0.95, RESTAURANTS, 0.03, TAXIS, 0.02))));
            LedgerTurnStubs.scriptStored();

            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                    .extractIntents(RequestFixtures.requestWithCatalogue("coffee 4.50 EUR", CATALOGUE, "Dining"));
            log.info("response: {}", response);

            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());

            List<RecordedCall> calls = LedgerTurnStubs.recordedCalls();
            assertThat(calls).hasSize(1);
            assertThat(calls.getFirst().method()).isEqualTo("FileExpenseProposal");
            FileExpenseProposalRequest filed =
                    (FileExpenseProposalRequest) calls.getFirst().request();
            assertThat(filed.getGrouping()).isEqualTo("Dining");
            assertThat(filed.getCategory()).isEqualTo("Cafés");
            assertThat(calls.getFirst().authorization()).isEqualTo(CALLER_TOKEN);

            JsonNode providerRequest = CapturedRequestUtils.body(
                    CapturedRequestUtils.chatCompletionRequests().getFirst());
            assertThat(providerRequest.path("tools").isMissingNode()
                            || providerRequest.path("tools").isEmpty())
                    .isTrue();

            assertThat(sampleOrZero(scrape(actuatorPort), turns, "mode=\"classifier\"", "outcome=\"acted_on\""))
                    .isEqualTo(before + 1);
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the ledger answers the first filing UNAVAILABLE - then the call fails UNAVAILABLE and "
                + "no second filing is sent")
        void whenLedgerAnswersFirstFilingUnavailable_thenCallFailsUnavailableAndNoSecondFiling() {
            WireMockStubs.stubChatCompletion(
                    ChatCompletionFixtures.readOut(
                            """
                    {"expenses": [
                      {"description": "coffee", "merchant": null, "amount": "4.50", "currency": "EUR"},
                      {"description": "taxi", "merchant": null, "amount": "12.00", "currency": "EUR"}
                    ], "periods": []}"""));
            ClassifierStubs.stubAnswered(SystemOneFixtures.answer(
                    new QuestionAnswer("0", CAFES, 0.95, Map.of(CAFES, 0.95, RESTAURANTS, 0.03, TAXIS, 0.02)),
                    new QuestionAnswer("1", TAXIS, 0.9, Map.of(CAFES, 0.05, RESTAURANTS, 0.05, TAXIS, 0.9))));
            LedgerTurnStubs.scriptRefused(Status.UNAVAILABLE);

            assertThatThrownBy(() -> AuthorizedStubs.withCallerToken(intentExtractionStub, CALLER_TOKEN)
                            .extractIntents(RequestFixtures.requestWithCatalogue(
                                    "coffee 4.50 EUR and a taxi 12 EUR", CATALOGUE, "Dining")))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAVAILABLE);

            assertThat(LedgerTurnStubs.recordedCalls()).hasSize(1);
        }
    }
}
