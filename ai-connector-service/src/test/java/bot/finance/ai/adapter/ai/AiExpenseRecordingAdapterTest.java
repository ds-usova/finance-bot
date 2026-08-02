package bot.finance.ai.adapter.ai;

import bot.finance.ai.adapter.grpc.CallerTokenTestSupport;
import bot.finance.ai.common.AiAdapterTest;
import bot.finance.ai.common.CapturedRequestUtils;
import bot.finance.ai.common.ChatCompletionFixtures;
import bot.finance.ai.common.JsonUtils;
import bot.finance.ai.common.McpLedgerStubs;
import bot.finance.ai.common.WireMockStubs;
import bot.finance.ai.common.WireMockSupport;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.value.CurrencyCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AiAdapterTest
class AiExpenseRecordingAdapterTest {

    private static final String SYSTEM_PROMPT_RESOURCE = "prompts/record-expenses.st";
    private static final String TEXT = "spent 15 euros on lunch";
    private static final List<String> KNOWN_CATEGORY_LABELS = List.of("Food > Lunch", "Insurance > Travel");
    private static final String CALLER_TOKEN_1 = "Bearer caller-token-1";
    private static final String CALLER_TOKEN_2 = "Bearer caller-token-2";

    private static final String LUNCH_ARGUMENTS = """
            {"category":"Lunch","description":"lunch","amountMinorUnits":1500,"currencyCode":"EUR"}""";
    private static final String CAB_ARGUMENTS = """
            {"category":"Travel","description":"cab","amountMinorUnits":2000,"currencyCode":"EUR"}""";

    @Autowired
    private AiExpenseRecordingAdapter adapter;

    @AfterEach
    void tearDown() {
        WireMockSupport.SERVER.resetAll();
    }

    private void record(String callerToken, Optional<CurrencyCode> assumedCurrency) {
        CallerTokenTestSupport.withCallerToken(callerToken,
                () -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, assumedCurrency));
    }

    private void recordInEuros(String callerToken) {
        record(callerToken, Optional.of(CurrencyCode.of("EUR")));
    }

    /**
     * The content the request carried back to the provider as the result of the tool call {@code toolCallId}, or
     * an empty string if it carried none.
     */
    private static String toolResultContent(LoggedRequest request, String toolCallId) {
        for (JsonNode message : CapturedRequestUtils.body(request).get("messages")) {
            if ("tool".equals(message.path("role").asText())
                    && toolCallId.equals(message.path("tool_call_id").asText())) {
                return message.path("content").asText();
            }
        }
        return "";
    }

    @Nested
    @DisplayName("record()")
    class Record {

        @Test
        @DisplayName("when the provider answers one create_expense_proposal tool call and the ledger accepts it, "
                + "with a caller token held for the turn - then the ledger receives exactly one tool call carrying "
                + "the model's arguments, every request on the wire carries that token as Authorization, and the "
                + "call returns without throwing")
        void whenOneAcceptedToolCallWithCallerToken_thenLedgerReceivesItEveryRequestCarriesTokenAndNoExceptionThrown() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-1", LUNCH_ARGUMENTS)),
                    ChatCompletionFixtures.textResponse("recorded"));

            assertThatCode(() -> recordInEuros(CALLER_TOKEN_1)).doesNotThrowAnyException();

            List<LoggedRequest> toolCalls = CapturedRequestUtils.toolCallRequests();
            assertThat(toolCalls).hasSize(1);
            JsonNode arguments = CapturedRequestUtils.toolCallArguments(toolCalls.get(0));
            assertThat(arguments.get("category").asText()).isEqualTo("Lunch");
            assertThat(arguments.get("description").asText()).isEqualTo("lunch");
            assertThat(arguments.get("amountMinorUnits").asLong()).isEqualTo(1500L);
            assertThat(arguments.get("currencyCode").asText()).isEqualTo("EUR");

            List<LoggedRequest> mcpRequests = CapturedRequestUtils.mcpRequests();
            assertThat(mcpRequests).isNotEmpty();
            assertThat(mcpRequests)
                    .extracting(request -> request.getHeader("Authorization"))
                    .containsOnly(CALLER_TOKEN_1);
        }

        @Test
        @DisplayName("when the model's tool call carries a merchant argument - then merchant reaches the ledger "
                + "verbatim")
        void whenToolCallCarriesMerchant_thenMerchantReachesLedgerVerbatim() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1", """
                            {"category":"Lunch","description":"lunch","amountMinorUnits":1500,\
                            "currencyCode":"EUR","merchant":"Deli Co"}""")),
                    ChatCompletionFixtures.textResponse("recorded"));

            recordInEuros(CALLER_TOKEN_1);

            List<LoggedRequest> toolCalls = CapturedRequestUtils.toolCallRequests();
            assertThat(toolCalls).hasSize(1);
            assertThat(CapturedRequestUtils.toolCallArguments(toolCalls.get(0)).get("merchant").asText())
                    .isEqualTo("Deli Co");
        }

        @Test
        @DisplayName("when record() is called with labels, a text and an assumed currency - then the provider's "
                + "request carries record-expenses.st verbatim as the system message, and a user message holding "
                + "the labels, the currency code and the text; its tool schema names create_expense_proposal with "
                + "the six arguments the ledger declares")
        void whenCalledWithLabelsTextAndCurrency_thenRequestCarriesSystemPromptUserMessageAndToolSchema() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.textResponse("nothing to record"));

            recordInEuros(CALLER_TOKEN_1);

            List<LoggedRequest> chatRequests = CapturedRequestUtils.chatCompletionRequests();
            assertThat(chatRequests).isNotEmpty();
            JsonNode body = CapturedRequestUtils.body(chatRequests.get(0));

            assertThat(CapturedRequestUtils.messageContent(body, "system"))
                    .isEqualTo(JsonUtils.readJsonResourceAsString(SYSTEM_PROMPT_RESOURCE));

            String userMessage = CapturedRequestUtils.messageContent(body, "user");
            assertThat(userMessage)
                    .contains(KNOWN_CATEGORY_LABELS.get(0))
                    .contains(KNOWN_CATEGORY_LABELS.get(1))
                    .contains("EUR")
                    .contains(TEXT);

            JsonNode tool = body.get("tools").get(0);
            assertThat(tool.get("type").asText()).isEqualTo("function");
            assertThat(tool.get("function").get("name").asText()).isEqualTo("create_expense_proposal");
            JsonNode properties = tool.get("function").get("parameters").get("properties");
            assertThat(properties.fieldNames()).toIterable().containsExactlyInAnyOrder(
                    "category", "parentCategory", "description", "merchant", "amountMinorUnits", "currencyCode");
        }

        @Test
        @DisplayName("when no assumed currency is given - then the user message says an amount with no currency "
                + "is left unrecorded, and names no currency code")
        void whenNoAssumedCurrency_thenUserMessageSaysUnrecordedAndNamesNoCurrencyCode() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.textResponse("nothing to record"));

            record(CALLER_TOKEN_1, Optional.empty());

            List<LoggedRequest> chatRequests = CapturedRequestUtils.chatCompletionRequests();
            assertThat(chatRequests).isNotEmpty();
            String userMessage =
                    CapturedRequestUtils.messageContent(CapturedRequestUtils.body(chatRequests.get(0)), "user");
            assertThat(userMessage).contains("unrecorded");
            assertThat(userMessage).doesNotContainPattern("\\b[A-Z]{3}\\b");
        }

        @Test
        @DisplayName("when the ledger answers the first tool call with a tool error result, then accepts the "
                + "corrected one - then the refusal text reaches the provider as that tool call's result, the "
                + "second tool call is made, and the call returns without throwing")
        void whenLedgerRefusesFirstToolCallThenAcceptsCorrected_thenRefusalReachesProviderSecondCallMadeAndNoExceptionThrown() {
            McpLedgerStubs.stubCreateExpenseProposalRefusedThenAccepted();
            String correctedArguments = """
                    {"category":"Travel","parentCategory":"Insurance","description":"cab",\
                    "amountMinorUnits":2000,"currencyCode":"EUR"}""";
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-1", CAB_ARGUMENTS)),
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-2", correctedArguments)),
                    ChatCompletionFixtures.textResponse("recorded"));

            assertThatCode(() -> recordInEuros(CALLER_TOKEN_1)).doesNotThrowAnyException();

            assertThat(CapturedRequestUtils.toolCallRequests()).hasSize(2);
            assertThat(CapturedRequestUtils.chatCompletionRequests())
                    .anyMatch(request -> toolResultContent(request, "call-1")
                            .contains("categories named Travel exist"));
        }

        @Test
        @DisplayName("when the ledger refuses the same expense twice - then the call returns without throwing and "
                + "no proposal is recorded for that expense")
        void whenLedgerRefusesSameExpenseTwice_thenReturnsWithoutThrowing() {
            McpLedgerStubs.stubCreateExpenseProposalRefusedTwice();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-1", CAB_ARGUMENTS)),
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-2", CAB_ARGUMENTS)),
                    ChatCompletionFixtures.textResponse("could not record"));

            assertThatCode(() -> recordInEuros(CALLER_TOKEN_1)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when two turns run in succession under different caller tokens, against one long-lived "
                + "client - then each turn's tool call carries its own token, and neither carries the other's")
        void whenTwoTurnsRunWithDifferentCallerTokens_thenEachToolCallCarriesOnlyItsOwnToken() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall("call-1", LUNCH_ARGUMENTS)),
                    ChatCompletionFixtures.textResponse("recorded"),
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-2", """
                            {"category":"Lunch","description":"dinner","amountMinorUnits":2500,\
                            "currencyCode":"EUR"}""")),
                    ChatCompletionFixtures.textResponse("recorded"));

            recordInEuros(CALLER_TOKEN_1);
            recordInEuros(CALLER_TOKEN_2);

            List<LoggedRequest> toolCalls = CapturedRequestUtils.toolCallRequests();
            assertThat(toolCalls).hasSize(2);
            assertThat(toolCalls.get(0).getHeader("Authorization")).isEqualTo(CALLER_TOKEN_1);
            assertThat(toolCalls.get(1).getHeader("Authorization")).isEqualTo(CALLER_TOKEN_2);
        }

        @Test
        @DisplayName("when the provider answers with text and no tool call - then the call returns and the ledger "
                + "receives no tool call")
        void whenProviderAnswersTextOnly_thenReturnsAndLedgerReceivesNoToolCall() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.textResponse("nothing to record"));

            assertThatCode(() -> recordInEuros(CALLER_TOKEN_1)).doesNotThrowAnyException();

            assertThat(CapturedRequestUtils.toolCallRequests()).isEmpty();
        }

        @Test
        @DisplayName("when the provider responds 500 - then it throws ExpenseRecordingFailedException, not a "
                + "Spring AI or HTTP-client exception")
        void whenProviderRespondsServerError_thenThrowsExpenseRecordingFailedException() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionServerError();

            assertThatThrownBy(() -> recordInEuros(CALLER_TOKEN_1))
                    .isInstanceOf(ExpenseRecordingFailedException.class);
        }

        @Test
        @DisplayName("when the ledger's endpoint fails the transport under the tool call - then it throws "
                + "ExpenseRecordingFailedException")
        void whenLedgerTransportFails_thenThrowsExpenseRecordingFailedException() {
            McpLedgerStubs.stubCreateExpenseProposalTransportFailure();
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.toolCallResponse(
                    ChatCompletionFixtures.toolCall("call-1", LUNCH_ARGUMENTS)));

            assertThatThrownBy(() -> recordInEuros(CALLER_TOKEN_1))
                    .isInstanceOf(ExpenseRecordingFailedException.class);
        }

        @Test
        @DisplayName("when no caller token is held for the turn - then it throws ExpenseRecordingFailedException")
        void whenNoCallerTokenHeld_thenThrowsExpenseRecordingFailedException() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.textResponse("irrelevant"));

            assertThatThrownBy(() -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, Optional.of(CurrencyCode.of("EUR"))))
                    .isInstanceOf(ExpenseRecordingFailedException.class);
        }

    }

}
