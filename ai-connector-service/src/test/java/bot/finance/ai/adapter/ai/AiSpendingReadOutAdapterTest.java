package bot.finance.ai.adapter.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.application.dto.ReadOutExpense;
import bot.finance.ai.application.dto.ReadOutPeriod;
import bot.finance.ai.application.dto.SpendingReadOut;
import bot.finance.ai.common.boot.AiAdapterTest;
import bot.finance.ai.common.containers.WireMockSupport;
import bot.finance.ai.common.fixtures.ChatCompletionFixtures;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.stubs.CapturedRequestUtils;
import bot.finance.ai.common.stubs.WireMockStubs;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.value.CurrencyCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

@AiAdapterTest
class AiSpendingReadOutAdapterTest {

    private static final String TEXT = "spent 15 euros at Deli Co and 3.50 on coffee";
    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 10, 13);
    private static final Optional<CurrencyCode> EUR = Optional.of(CurrencyCode.of("EUR"));

    @Autowired
    private AiSpendingReadOutAdapter adapter;

    @BeforeEach
    void setUp() {
        WireMockSupport.SERVER.resetAll();
    }

    @AfterEach
    void tearDown() {
        WireMockSupport.SERVER.resetAll();
    }

    private static void stubReadOut(String json) {
        WireMockStubs.stubChatCompletion(ChatCompletionFixtures.readOut(json));
    }

    private static JsonNode onlyChatRequestBody() {
        List<LoggedRequest> chatRequests = CapturedRequestUtils.chatCompletionRequests();
        assertThat(chatRequests).hasSize(1);
        return CapturedRequestUtils.body(chatRequests.get(0));
    }

    @Nested
    @DisplayName("readOut()")
    class ReadOut {

        @Test
        @DisplayName("when the provider answers two expenses and a period - then the read-out holds them, "
                + "a null merchant absent")
        void whenProviderAnswersTwoExpensesAndPeriod_thenReadOutHoldsThemWithNullMerchantAbsent() {
            stubReadOut(
                    """
                    {
                      "expenses": [
                        {"description": "lunch", "merchant": "Deli Co", "amount": "15.00", "currency": "EUR"},
                        {"description": "coffee", "merchant": null, "amount": "3.50", "currency": "EUR"}
                      ],
                      "periods": [{"from": "2026-10-12", "to": "2026-10-12"}]
                    }""");

            SpendingReadOut readOut = adapter.readOut(TEXT, CURRENT_DATE, EUR);

            assertThat(readOut.expenses())
                    .containsExactly(
                            new ReadOutExpense("lunch", "Deli Co", "15.00", "EUR"),
                            new ReadOutExpense("coffee", null, "3.50", "EUR"));
            assertThat(readOut.periods())
                    .containsExactly(new ReadOutPeriod(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12)));
        }

        @Test
        @DisplayName("when the message is read out - then the request has a JSON-schema response format and no tools")
        void whenMessageIsReadOut_thenRequestCarriesJsonSchemaResponseFormatAndNoTools() {
            stubReadOut("""
                    {"expenses": [], "periods": []}""");

            adapter.readOut(TEXT, CURRENT_DATE, EUR);

            JsonNode body = onlyChatRequestBody();
            assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_schema");
            assertThat(body.path("tools").isMissingNode() || body.path("tools").isEmpty())
                    .isTrue();
        }

        @Test
        @DisplayName("when the message is read out - then the request names no grouping or category")
        void whenMessageIsReadOut_thenRequestNamesNoGroupingOrCategory() {
            stubReadOut("""
                    {"expenses": [], "periods": []}""");

            adapter.readOut(TEXT, CURRENT_DATE, EUR);

            assertThat(onlyChatRequestBody().toString())
                    .doesNotContain(RequestFixtures.DEFAULT_CATEGORY_GROUPINGS.get(0))
                    .doesNotContain(RequestFixtures.DEFAULT_CATEGORY_GROUPINGS.get(1))
                    .doesNotContain(RequestFixtures.DEFAULT_CATEGORY_GROUPINGS.get(2));
        }

        @Test
        @DisplayName("when the message is read out with a day and an assumed currency - then the instructions state "
                + "both")
        void whenReadOutWithDayAndAssumedCurrency_thenInstructionsStateBoth() {
            stubReadOut("""
                    {"expenses": [], "periods": []}""");

            adapter.readOut(TEXT, CURRENT_DATE, EUR);

            String userMessage = CapturedRequestUtils.messageContent(onlyChatRequestBody(), "user");
            assertThat(userMessage)
                    .contains("Today is 2026-10-13 (UTC)")
                    .contains("states an amount without one: EUR")
                    .contains(TEXT);
        }

        @Test
        @DisplayName("when the provider responds 500 - then it throws ExpenseRecordingFailedException")
        void whenProviderRespondsServerError_thenThrowsExpenseRecordingFailedException() {
            WireMockStubs.stubChatCompletionServerError();

            assertThatThrownBy(() -> adapter.readOut(TEXT, CURRENT_DATE, EUR))
                    .isInstanceOf(ExpenseRecordingFailedException.class);
        }

        @Test
        @DisplayName("when the provider drops the connection without answering - then it throws "
                + "ExpenseRecordingFailedException")
        void whenProviderDoesNotAnswer_thenThrowsExpenseRecordingFailedException() {
            WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(WireMockStubs.CHAT_COMPLETIONS_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            assertThatThrownBy(() -> adapter.readOut(TEXT, CURRENT_DATE, EUR))
                    .isInstanceOf(ExpenseRecordingFailedException.class);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    "I could not read that message",
                    """
                    {"expenses": "lunch", "periods": []}""",
                    """
                    {"expenses": [], "periods": [{"from": 5, "to": {}}]}"""
                })
        @DisplayName("when the provider answers content outside the read-out shape - then it throws "
                + "ExpenseRecordingFailedException")
        void whenProviderAnswersContentOutsideShape_thenThrowsExpenseRecordingFailedException(String content) {
            stubReadOut(content);

            assertThatThrownBy(() -> adapter.readOut(TEXT, CURRENT_DATE, EUR))
                    .isInstanceOf(ExpenseRecordingFailedException.class);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"12/10/2026", "2026-13-01", "yesterday", ""})
        @DisplayName("when a period day is not written YYYY-MM-DD - then it throws ExpenseRecordingFailedException")
        void whenPeriodDayIsNotIsoDate_thenThrowsExpenseRecordingFailedException(String day) {
            stubReadOut("""
                    {"expenses": [], "periods": [{"from": "%s", "to": "2026-10-12"}]}"""
                    .formatted(day));

            assertThatThrownBy(() -> adapter.readOut(TEXT, CURRENT_DATE, EUR))
                    .isInstanceOf(ExpenseRecordingFailedException.class);
        }
    }
}
