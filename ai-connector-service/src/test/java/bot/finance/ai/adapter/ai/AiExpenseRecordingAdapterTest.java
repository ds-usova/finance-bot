package bot.finance.ai.adapter.ai;

import bot.finance.ai.adapter.grpc.CallerTokenTestSupport;
import bot.finance.ai.common.AiAdapterTest;
import bot.finance.ai.common.ChatCompletionFixtures;
import bot.finance.ai.common.JsonUtils;
import bot.finance.ai.common.McpLedgerStubs;
import bot.finance.ai.common.WireMockStubs;
import bot.finance.ai.common.WireMockSupport;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.value.CurrencyCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private AiExpenseRecordingAdapter adapter;

    @AfterEach
    void tearDown() {
        WireMockSupport.SERVER.resetAll();
    }

    private static List<LoggedRequest> capturedChatRequests() {
        return WireMockSupport.SERVER.findAll(postRequestedFor(urlPathEqualTo(WireMockStubs.CHAT_COMPLETIONS_PATH)));
    }

    private static List<LoggedRequest> capturedMcpRequests() {
        return WireMockSupport.SERVER.findAll(postRequestedFor(urlPathEqualTo(McpLedgerStubs.MCP_PATH)));
    }

    private static JsonNode requestBody(LoggedRequest request) throws JsonProcessingException {
        return MAPPER.readTree(request.getBodyAsString());
    }

    private static String messageContent(JsonNode requestBody, String role) {
        for (JsonNode message : requestBody.get("messages")) {
            if (role.equals(message.get("role").asText())) {
                return message.get("content").asText();
            }
        }
        throw new AssertionError("no message with role " + role + " in " + requestBody);
    }

    /**
     * Among the requests the session sent, those invoking {@code create_expense_proposal} — as opposed to the
     * handshake or {@code tools/list} requests the session also performs.
     */
    private static List<LoggedRequest> toolCallRequests(List<LoggedRequest> mcpRequests) throws JsonProcessingException {
        List<LoggedRequest> calls = new ArrayList<>();
        for (LoggedRequest request : mcpRequests) {
            if ("create_expense_proposal".equals(requestBody(request).at("/params/name").asText())) {
                calls.add(request);
            }
        }
        return calls;
    }

    private static JsonNode toolCallArguments(LoggedRequest toolCallRequest) throws JsonProcessingException {
        return requestBody(toolCallRequest).at("/params/arguments");
    }

    @Nested
    @DisplayName("record()")
    class Record {

        @Test
        @DisplayName("when the provider answers one create_expense_proposal tool call and the ledger accepts it, "
                + "with a caller token held for the turn - then the ledger receives exactly one tool call carrying "
                + "the model's arguments, every request on the wire carries that token as Authorization, and the "
                + "call returns without throwing")
        void whenOneAcceptedToolCallWithCallerToken_thenLedgerReceivesItEveryRequestCarriesTokenAndNoExceptionThrown()
                throws JsonProcessingException {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1",
                            "{\"category\":\"Lunch\",\"description\":\"lunch\",\"amountMinorUnits\":1500,"
                                    + "\"currencyCode\":\"EUR\"}")),
                    ChatCompletionFixtures.textResponse("recorded"));

            assertThatCode(() -> CallerTokenTestSupport.withCallerToken(CALLER_TOKEN_1,
                    () -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, Optional.of(CurrencyCode.of("EUR")))))
                    .doesNotThrowAnyException();

            List<LoggedRequest> mcpRequests = capturedMcpRequests();
            List<LoggedRequest> toolCalls = toolCallRequests(mcpRequests);
            assertThat(toolCalls).hasSize(1);
            JsonNode arguments = toolCallArguments(toolCalls.get(0));
            assertThat(arguments.get("category").asText()).isEqualTo("Lunch");
            assertThat(arguments.get("description").asText()).isEqualTo("lunch");
            assertThat(arguments.get("amountMinorUnits").asLong()).isEqualTo(1500L);
            assertThat(arguments.get("currencyCode").asText()).isEqualTo("EUR");
            assertThat(mcpRequests).isNotEmpty();
            for (LoggedRequest request : mcpRequests) {
                assertThat(request.getHeader("Authorization")).isEqualTo(CALLER_TOKEN_1);
            }
        }

        @Test
        @DisplayName("when the model's tool call carries a merchant argument - then merchant reaches the ledger "
                + "verbatim")
        void whenToolCallCarriesMerchant_thenMerchantReachesLedgerVerbatim() throws JsonProcessingException {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1",
                            "{\"category\":\"Lunch\",\"description\":\"lunch\",\"amountMinorUnits\":1500,"
                                    + "\"currencyCode\":\"EUR\",\"merchant\":\"Deli Co\"}")),
                    ChatCompletionFixtures.textResponse("recorded"));

            CallerTokenTestSupport.withCallerToken(CALLER_TOKEN_1,
                    () -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, Optional.of(CurrencyCode.of("EUR"))));

            List<LoggedRequest> toolCalls = toolCallRequests(capturedMcpRequests());
            assertThat(toolCalls).hasSize(1);
            assertThat(toolCallArguments(toolCalls.get(0)).get("merchant").asText()).isEqualTo("Deli Co");
        }

        @Test
        @DisplayName("when record() is called with labels, a text and an assumed currency - then the provider's "
                + "request carries record-expenses.st verbatim as the system message, and a user message holding "
                + "the labels, the currency code and the text; its tool schema names create_expense_proposal with "
                + "the six arguments the ledger declares")
        void whenCalledWithLabelsTextAndCurrency_thenRequestCarriesSystemPromptUserMessageAndToolSchema()
                throws JsonProcessingException {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.textResponse("nothing to record"));

            CallerTokenTestSupport.withCallerToken(CALLER_TOKEN_1,
                    () -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, Optional.of(CurrencyCode.of("EUR"))));

            List<LoggedRequest> chatRequests = capturedChatRequests();
            assertThat(chatRequests).isNotEmpty();
            JsonNode body = requestBody(chatRequests.get(0));

            assertThat(messageContent(body, "system"))
                    .isEqualTo(JsonUtils.readJsonResourceAsString(SYSTEM_PROMPT_RESOURCE));

            String userMessage = messageContent(body, "user");
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
        void whenNoAssumedCurrency_thenUserMessageSaysUnrecordedAndNamesNoCurrencyCode() throws JsonProcessingException {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.textResponse("nothing to record"));

            CallerTokenTestSupport.withCallerToken(CALLER_TOKEN_1,
                    () -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, Optional.empty()));

            List<LoggedRequest> chatRequests = capturedChatRequests();
            assertThat(chatRequests).isNotEmpty();
            String userMessage = messageContent(requestBody(chatRequests.get(0)), "user");
            assertThat(userMessage).contains("unrecorded");
            assertThat(userMessage).doesNotContainPattern("\\b[A-Z]{3}\\b");
        }

        @Test
        @DisplayName("when the ledger answers the first tool call with a tool error result, then accepts the "
                + "corrected one - then the refusal text reaches the provider as that tool call's result, the "
                + "second tool call is made, and the call returns without throwing")
        void whenLedgerRefusesFirstToolCallThenAcceptsCorrected_thenRefusalReachesProviderSecondCallMadeAndNoExceptionThrown()
                throws JsonProcessingException {
            McpLedgerStubs.stubCreateExpenseProposalRefusedThenAccepted();
            String firstArgs = "{\"category\":\"Travel\",\"description\":\"cab\",\"amountMinorUnits\":2000,"
                    + "\"currencyCode\":\"EUR\"}";
            String correctedArgs = "{\"category\":\"Travel\",\"parentCategory\":\"Insurance\",\"description\":\"cab\","
                    + "\"amountMinorUnits\":2000,\"currencyCode\":\"EUR\"}";
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1", firstArgs)),
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-2", correctedArgs)),
                    ChatCompletionFixtures.textResponse("recorded"));

            assertThatCode(() -> CallerTokenTestSupport.withCallerToken(CALLER_TOKEN_1,
                    () -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, Optional.of(CurrencyCode.of("EUR")))))
                    .doesNotThrowAnyException();

            List<LoggedRequest> toolCalls = toolCallRequests(capturedMcpRequests());
            assertThat(toolCalls).hasSize(2);

            boolean refusalReachedProvider = capturedChatRequests().stream().anyMatch(request -> {
                try {
                    JsonNode requestJson = requestBody(request);
                    for (JsonNode message : requestJson.get("messages")) {
                        if ("tool".equals(message.path("role").asText())
                                && "call-1".equals(message.path("tool_call_id").asText())) {
                            return message.get("content").asText().contains("categories named Travel exist");
                        }
                    }
                    return false;
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
            assertThat(refusalReachedProvider).isTrue();
        }

        @Test
        @DisplayName("when the ledger refuses the same expense twice - then the call returns without throwing and "
                + "no proposal is recorded for that expense")
        void whenLedgerRefusesSameExpenseTwice_thenReturnsWithoutThrowing() {
            McpLedgerStubs.stubCreateExpenseProposalRefusedTwice();
            String args = "{\"category\":\"Travel\",\"description\":\"cab\",\"amountMinorUnits\":2000,"
                    + "\"currencyCode\":\"EUR\"}";
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1", args)),
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-2", args)),
                    ChatCompletionFixtures.textResponse("could not record"));

            assertThatCode(() -> CallerTokenTestSupport.withCallerToken(CALLER_TOKEN_1,
                    () -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, Optional.of(CurrencyCode.of("EUR")))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when two turns run in succession under different caller tokens, against one long-lived "
                + "client - then each turn's tool call carries its own token, and neither carries the other's")
        void whenTwoTurnsRunWithDifferentCallerTokens_thenEachToolCallCarriesOnlyItsOwnToken()
                throws JsonProcessingException {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1",
                            "{\"category\":\"Lunch\",\"description\":\"lunch\",\"amountMinorUnits\":1500,"
                                    + "\"currencyCode\":\"EUR\"}")),
                    ChatCompletionFixtures.textResponse("recorded"),
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-2",
                            "{\"category\":\"Lunch\",\"description\":\"dinner\",\"amountMinorUnits\":2500,"
                                    + "\"currencyCode\":\"EUR\"}")),
                    ChatCompletionFixtures.textResponse("recorded"));

            CallerTokenTestSupport.withCallerToken(CALLER_TOKEN_1,
                    () -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, Optional.of(CurrencyCode.of("EUR"))));
            CallerTokenTestSupport.withCallerToken(CALLER_TOKEN_2,
                    () -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, Optional.of(CurrencyCode.of("EUR"))));

            List<LoggedRequest> toolCalls = toolCallRequests(capturedMcpRequests());
            assertThat(toolCalls).hasSize(2);
            assertThat(toolCalls.get(0).getHeader("Authorization")).isEqualTo(CALLER_TOKEN_1);
            assertThat(toolCalls.get(1).getHeader("Authorization")).isEqualTo(CALLER_TOKEN_2);
        }

        @Test
        @DisplayName("when the provider answers with text and no tool call - then the call returns and the ledger "
                + "receives no tool call")
        void whenProviderAnswersTextOnly_thenReturnsAndLedgerReceivesNoToolCall() throws JsonProcessingException {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.textResponse("nothing to record"));

            assertThatCode(() -> CallerTokenTestSupport.withCallerToken(CALLER_TOKEN_1,
                    () -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, Optional.of(CurrencyCode.of("EUR")))))
                    .doesNotThrowAnyException();

            assertThat(toolCallRequests(capturedMcpRequests())).isEmpty();
        }

        @Test
        @DisplayName("when the provider responds 500 - then it throws ExpenseRecordingFailedException, not a "
                + "Spring AI or HTTP-client exception")
        void whenProviderRespondsServerError_thenThrowsExpenseRecordingFailedException() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionServerError();

            assertThatThrownBy(() -> CallerTokenTestSupport.withCallerToken(CALLER_TOKEN_1,
                    () -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, Optional.of(CurrencyCode.of("EUR")))))
                    .isInstanceOf(ExpenseRecordingFailedException.class);
        }

        @Test
        @DisplayName("when the ledger's endpoint fails the transport under the tool call - then it throws "
                + "ExpenseRecordingFailedException")
        void whenLedgerTransportFails_thenThrowsExpenseRecordingFailedException() {
            McpLedgerStubs.stubCreateExpenseProposalTransportFailure();
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.toolCallResponse(
                    ChatCompletionFixtures.toolCall("call-1",
                            "{\"category\":\"Lunch\",\"description\":\"lunch\",\"amountMinorUnits\":1500,"
                                    + "\"currencyCode\":\"EUR\"}")));

            assertThatThrownBy(() -> CallerTokenTestSupport.withCallerToken(CALLER_TOKEN_1,
                    () -> adapter.record(TEXT, KNOWN_CATEGORY_LABELS, Optional.of(CurrencyCode.of("EUR")))))
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
