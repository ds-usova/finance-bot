package bot.finance.ai.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.adapter.grpc.CallerTokenTestSupport;
import bot.finance.ai.common.boot.AiAdapterTest;
import bot.finance.ai.common.containers.WireMockSupport;
import bot.finance.ai.common.fixtures.ChatCompletionFixtures;
import bot.finance.ai.common.fixtures.JsonUtils;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.stubs.CapturedRequestUtils;
import bot.finance.ai.common.stubs.McpLedgerStubs;
import bot.finance.ai.common.stubs.WireMockStubs;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.value.CurrencyCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

@AiAdapterTest
class AiExpenseRecordingAdapterTest {

    private static final String SYSTEM_PROMPT_RESOURCE = "prompts/record-expenses.st";
    private static final String TEXT = "spent 15 euros on lunch";
    private static final List<String> CATEGORY_GROUPINGS = RequestFixtures.DEFAULT_CATEGORY_GROUPINGS;
    private static final String CATCH_ALL_GROUPING = RequestFixtures.DEFAULT_CATCH_ALL;
    private static final String CALLER_TOKEN_1 = "Bearer caller-token-1";
    private static final String CALLER_TOKEN_2 = "Bearer caller-token-2";
    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 5);

    /** The grouping the lookup scenarios ask about — one of the fixture's own, so no literal is repeated. */
    private static final String LOOKUP_GROUPING = RequestFixtures.DEFAULT_CATEGORY_GROUPINGS.get(0);

    private static final String LUNCH_ARGUMENTS =
            """
            {"category":"Lunch","description":"lunch","amount":"15.00","currencyCode":"EUR"}""";
    private static final String CAB_ARGUMENTS =
            """
            {"category":"Travel","description":"cab","amount":"20.00","currencyCode":"EUR"}""";

    @Autowired
    private AiExpenseRecordingAdapter adapter;

    /**
     * Reset before as well as after, so a test starts on an empty journal whatever the class that ran before it
     * left on the wire — the server is a singleton, and every context pointed at it outlives its own class.
     */
    @BeforeEach
    void setUp() {
        WireMockSupport.SERVER.resetAll();
    }

    @AfterEach
    void tearDown() {
        WireMockSupport.SERVER.resetAll();
    }

    private void record(String callerToken, Optional<CurrencyCode> assumedCurrency) {
        CallerTokenTestSupport.withCallerToken(
                callerToken,
                () -> adapter.record(
                        TEXT, CATEGORY_GROUPINGS, CATCH_ALL_GROUPING, assumedCurrency, CURRENT_DATE, Optional.empty()));
    }

    private void recordInEuros(String callerToken) {
        record(callerToken, Optional.of(CurrencyCode.of("EUR")));
    }

    /**
     * The body of the first chat-completion request of a turn the provider answers with text only — what the
     * scenarios about the outgoing request read from.
     */
    private JsonNode chatRequestBody(Optional<CurrencyCode> assumedCurrency) {
        McpLedgerStubs.stubCreateExpenseProposalAccepted();
        WireMockStubs.stubChatCompletion(ChatCompletionFixtures.textResponse("nothing to record"));

        record(CALLER_TOKEN_1, assumedCurrency);

        List<LoggedRequest> chatRequests = CapturedRequestUtils.chatCompletionRequests();
        assertThat(chatRequests).isNotEmpty();
        return CapturedRequestUtils.body(chatRequests.get(0));
    }

    /** A turn where the provider looks a grouping up under {@code call-list-1}, then records, both accepted. */
    private static void stubLookupThenProposalTurn() {
        McpLedgerStubs.stubCreateExpenseProposalAccepted();
        McpLedgerStubs.stubListCategoriesAnswering(LOOKUP_GROUPING, List.of("Lunch"));
        WireMockStubs.stubChatCompletionSequence(
                ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall(
                        "call-list-1", ChatCompletionFixtures.LedgerTool.LIST_CATEGORIES, lookupArguments())),
                ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-2", LUNCH_ARGUMENTS)),
                ChatCompletionFixtures.textResponse("recorded"));
    }

    /** A turn where the ledger refuses the first proposal under {@code call-1} and accepts the corrected one. */
    private static void stubRefusedThenCorrectedTurn() {
        McpLedgerStubs.stubCreateExpenseProposalRefusedThenAccepted();
        String correctedArguments =
                """
                {"category":"Travel","grouping":"Insurance","description":"cab",\
                "amount":"20.00","currencyCode":"EUR"}""";
        WireMockStubs.stubChatCompletionSequence(
                ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1", CAB_ARGUMENTS)),
                ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-2", correctedArguments)),
                ChatCompletionFixtures.textResponse("recorded"));
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

    /** The {@code list_categories} arguments asking about {@link #LOOKUP_GROUPING}. */
    private static String lookupArguments() {
        return "{\"grouping\":\"" + LOOKUP_GROUPING + "\"}";
    }

    /**
     * The tool schema entry naming {@code functionName} among the {@code tools} array on a chat-completion
     * request body.
     */
    private static JsonNode toolNamed(JsonNode tools, String functionName) {
        for (JsonNode tool : tools) {
            if (functionName.equals(tool.path("function").path("name").asText())) {
                return tool;
            }
        }
        throw new AssertionError("no tool named " + functionName + " in " + tools);
    }

    @Nested
    @DisplayName("record()")
    class Record {

        @Test
        @DisplayName("when the provider answers one create_expense_proposal call - then the ledger receives the "
                + "model's arguments")
        void whenProviderAnswersOneAcceptedToolCall_thenLedgerReceivesTheModelsArguments() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1", LUNCH_ARGUMENTS)),
                    ChatCompletionFixtures.textResponse("recorded"));

            assertThatCode(() -> recordInEuros(CALLER_TOKEN_1)).doesNotThrowAnyException();

            List<LoggedRequest> toolCalls = CapturedRequestUtils.toolCallRequests();
            assertThat(toolCalls).hasSize(1);
            JsonNode arguments = CapturedRequestUtils.toolCallArguments(toolCalls.get(0));
            assertThat(arguments.get("category").asText()).isEqualTo("Lunch");
            assertThat(arguments.get("description").asText()).isEqualTo("lunch");
            assertThat(arguments.get("amount").asText()).isEqualTo("15.00");
            assertThat(arguments.get("currencyCode").asText()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when a caller token is held for the turn - then every request to the ledger carries it as "
                + "Authorization")
        void whenCallerTokenHeldForTheTurn_thenEveryLedgerRequestCarriesItAsAuthorization() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1", LUNCH_ARGUMENTS)),
                    ChatCompletionFixtures.textResponse("recorded"));

            recordInEuros(CALLER_TOKEN_1);

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
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall(
                                    "call-1",
                                    """
                            {"category":"Lunch","description":"lunch","amount":"15.00",\
                            "currencyCode":"EUR","merchant":"Deli Co"}""")),
                    ChatCompletionFixtures.textResponse("recorded"));

            recordInEuros(CALLER_TOKEN_1);

            List<LoggedRequest> toolCalls = CapturedRequestUtils.toolCallRequests();
            assertThat(toolCalls).hasSize(1);
            assertThat(CapturedRequestUtils.toolCallArguments(toolCalls.get(0))
                            .get("merchant")
                            .asText())
                    .isEqualTo("Deli Co");
        }

        @Test
        @DisplayName("when record() is called - then the provider's request carries record-expenses.st verbatim "
                + "as the system message")
        void whenCalled_thenSystemMessageIsTheRecordExpensesPromptVerbatim() {
            JsonNode body = chatRequestBody(Optional.of(CurrencyCode.of("EUR")));

            assertThat(CapturedRequestUtils.messageContent(body, "system"))
                    .isEqualTo(JsonUtils.readJsonResourceAsString(SYSTEM_PROMPT_RESOURCE));
        }

        @Test
        @DisplayName("when record() is called - then the user message holds the current date, the labels, the "
                + "currency and the text")
        void whenCalledWithLabelsTextAndCurrency_thenUserMessageHoldsDateLabelsCurrencyAndText() {
            JsonNode body = chatRequestBody(Optional.of(CurrencyCode.of("EUR")));

            String userMessage = CapturedRequestUtils.messageContent(body, "user");
            assertThat(userMessage)
                    .contains("Today is " + CURRENT_DATE + " (UTC)")
                    .contains(CATEGORY_GROUPINGS.get(0))
                    .contains(CATEGORY_GROUPINGS.get(1))
                    .contains(CATEGORY_GROUPINGS.get(2))
                    .contains(CATCH_ALL_GROUPING)
                    .contains("EUR")
                    .contains(TEXT)
                    .contains("sending that same grouping with it")
                    .doesNotContain(">");
        }

        @Test
        @DisplayName("when record() is called - then the tool schema names the three ledger tools with the "
                + "arguments each declares")
        void whenCalled_thenToolSchemaNamesTheLedgerToolsAndTheirArguments() {
            JsonNode body = chatRequestBody(Optional.of(CurrencyCode.of("EUR")));

            JsonNode tools = body.get("tools");
            assertThat(tools)
                    .extracting(tool -> tool.path("function").path("name").asText())
                    .containsExactlyInAnyOrder("create_expense_proposal", "list_categories", "summarize_spending");

            JsonNode createExpenseProposalTool = toolNamed(tools, "create_expense_proposal");
            assertThat(createExpenseProposalTool.get("type").asText()).isEqualTo("function");
            JsonNode createExpenseProposalProperties =
                    createExpenseProposalTool.get("function").get("parameters").get("properties");
            assertThat(createExpenseProposalProperties.fieldNames())
                    .toIterable()
                    .containsExactlyInAnyOrder(
                            "category", "grouping", "description", "merchant", "amount", "currencyCode");

            JsonNode listCategoriesTool = toolNamed(tools, "list_categories");
            assertThat(listCategoriesTool.get("type").asText()).isEqualTo("function");
            JsonNode listCategoriesProperties =
                    listCategoriesTool.get("function").get("parameters").get("properties");
            assertThat(listCategoriesProperties.fieldNames()).toIterable().containsExactly("grouping");

            JsonNode summarizeSpendingTool = toolNamed(tools, "summarize_spending");
            assertThat(summarizeSpendingTool.get("type").asText()).isEqualTo("function");
            JsonNode summarizeSpendingProperties =
                    summarizeSpendingTool.get("function").get("parameters").get("properties");
            assertThat(summarizeSpendingProperties.fieldNames()).toIterable().containsExactlyInAnyOrder("from", "to");
        }

        @Test
        @DisplayName("when the provider calls two ledger tools in one turn - then both calls reach the ledger "
                + "under the turn's token")
        void whenProviderListsCategoriesThenCreatesProposal_thenBothCallsReachLedgerUnderTheTurnsToken() {
            stubLookupThenProposalTurn();

            assertThatCode(() -> recordInEuros(CALLER_TOKEN_1)).doesNotThrowAnyException();

            List<LoggedRequest> listCategoriesCalls = CapturedRequestUtils.toolCallRequests("list_categories");
            List<LoggedRequest> createExpenseProposalCalls = CapturedRequestUtils.toolCallRequests();
            assertThat(listCategoriesCalls).hasSize(1);
            assertThat(createExpenseProposalCalls).hasSize(1);
            assertThat(listCategoriesCalls.get(0).getHeader("Authorization")).isEqualTo(CALLER_TOKEN_1);
            assertThat(createExpenseProposalCalls.get(0).getHeader("Authorization"))
                    .isEqualTo(CALLER_TOKEN_1);
        }

        @Test
        @DisplayName("when the ledger answers a list_categories call - then its answer reaches the provider as "
                + "that call's result")
        void whenLedgerAnswersListCategories_thenAnswerReachesProviderAsThatCallsResult() {
            stubLookupThenProposalTurn();

            recordInEuros(CALLER_TOKEN_1);

            assertThat(CapturedRequestUtils.chatCompletionRequests())
                    .anyMatch(
                            request -> toolResultContent(request, "call-list-1").contains("Lunch"));
        }

        @Test
        @DisplayName("when a list_categories call is refused and the provider records anyway - then the create "
                + "call reaches the ledger")
        void whenLedgerRefusesListCategoriesThenProviderCorrectsAndRecords_thenCreateCallReachesLedger() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            McpLedgerStubs.stubListCategoriesRefused();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall(
                            "call-list-1", ChatCompletionFixtures.LedgerTool.LIST_CATEGORIES, lookupArguments())),
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-2", LUNCH_ARGUMENTS)),
                    ChatCompletionFixtures.textResponse("recorded"));

            assertThatCode(() -> recordInEuros(CALLER_TOKEN_1)).doesNotThrowAnyException();

            assertThat(CapturedRequestUtils.toolCallRequests()).hasSize(1);
        }

        @Test
        @DisplayName("when no assumed currency is given - then the user message names no currency code and says "
                + "the amount goes unrecorded")
        void whenNoAssumedCurrency_thenUserMessageSaysUnrecordedAndNamesNoCurrencyCode() {
            String userMessage = CapturedRequestUtils.messageContent(chatRequestBody(Optional.empty()), "user");
            assertThat(userMessage).contains("unrecorded");

            String userMessageWithoutTodayLine = userMessage
                    .lines()
                    .filter(line -> !line.startsWith("Today is "))
                    .collect(Collectors.joining("\n"));
            assertThat(userMessageWithoutTodayLine).doesNotContainPattern("\\b[A-Z]{3}\\b");
        }

        @Test
        @DisplayName("when the ledger refuses the first tool call and accepts the corrected one - then both tool "
                + "calls reach the ledger")
        void whenLedgerRefusesFirstToolCallThenAcceptsCorrected_thenBothToolCallsReachLedger() {
            stubRefusedThenCorrectedTurn();

            assertThatCode(() -> recordInEuros(CALLER_TOKEN_1)).doesNotThrowAnyException();

            assertThat(CapturedRequestUtils.toolCallRequests()).hasSize(2);
        }

        @Test
        @DisplayName("when the ledger refuses the first tool call - then the refusal text reaches the provider as "
                + "that call's result")
        void whenLedgerRefusesFirstToolCall_thenRefusalTextReachesProviderAsThatCallsResult() {
            stubRefusedThenCorrectedTurn();

            recordInEuros(CALLER_TOKEN_1);

            assertThat(CapturedRequestUtils.chatCompletionRequests())
                    .anyMatch(
                            request -> toolResultContent(request, "call-1").contains("categories named Travel exist"));
        }

        @Test
        @DisplayName("when the ledger refuses the same expense twice - then the call returns without throwing")
        void whenLedgerRefusesSameExpenseTwice_thenReturnsWithoutThrowing() {
            McpLedgerStubs.stubCreateExpenseProposalRefusedTwice();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1", CAB_ARGUMENTS)),
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-2", CAB_ARGUMENTS)),
                    ChatCompletionFixtures.textResponse("could not record"));

            assertThatCode(() -> recordInEuros(CALLER_TOKEN_1)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when two turns run under different caller tokens - then each tool call carries its own "
                + "turn's token")
        void whenTwoTurnsRunWithDifferentCallerTokens_thenEachToolCallCarriesOnlyItsOwnToken() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall("call-1", LUNCH_ARGUMENTS)),
                    ChatCompletionFixtures.textResponse("recorded"),
                    ChatCompletionFixtures.toolCallResponse(
                            ChatCompletionFixtures.toolCall(
                                    "call-2",
                                    """
                            {"category":"Lunch","description":"dinner","amount":"25.00",\
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
                + "Spring AI exception")
        void whenProviderRespondsServerError_thenThrowsExpenseRecordingFailedException() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletionServerError();

            assertThatThrownBy(() -> recordInEuros(CALLER_TOKEN_1)).isInstanceOf(ExpenseRecordingFailedException.class);
        }

        /**
         * Runs against a context of its own, so the client meets the dead endpoint with no session in hand. The
         * stub carries no body matcher and so takes down the handshake as well as the tool call — the failure
         * this scenario is about. A client that had already handshaken would meet it mid-session instead, and
         * the turn would fail, or not, by whatever ran before.
         */
        @Test
        @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
        @DisplayName("when the ledger's endpoint fails the transport under the tool call - then it throws "
                + "ExpenseRecordingFailedException")
        void whenLedgerTransportFails_thenThrowsExpenseRecordingFailedException() {
            McpLedgerStubs.stubCreateExpenseProposalTransportFailure();
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.toolCallResponse(
                    ChatCompletionFixtures.toolCall("call-1", LUNCH_ARGUMENTS)));

            assertThatThrownBy(() -> recordInEuros(CALLER_TOKEN_1)).isInstanceOf(ExpenseRecordingFailedException.class);
        }

        @Test
        @DisplayName("when the provider calls summarize_spending - then the call reaches the ledger with the "
                + "period asked for")
        void whenProviderCallsSummarizeSpending_thenLedgerReceivesItWithTheAskedPeriod() {
            String from = "2026-07-27";
            String to = "2026-08-02";
            McpLedgerStubs.stubSummarizeSpendingAccepted(from, to);
            WireMockStubs.stubChatCompletionSequence(
                    ChatCompletionFixtures.toolCallResponse(ChatCompletionFixtures.toolCall(
                            "call-1",
                            ChatCompletionFixtures.LedgerTool.SUMMARIZE_SPENDING,
                            "{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}")),
                    ChatCompletionFixtures.textResponse("here you go"));

            assertThatCode(() -> recordInEuros(CALLER_TOKEN_1)).doesNotThrowAnyException();

            List<LoggedRequest> summarizeSpendingCalls = CapturedRequestUtils.toolCallRequests("summarize_spending");
            assertThat(summarizeSpendingCalls).hasSize(1);
            assertThat(summarizeSpendingCalls.get(0).getHeader("Authorization")).isEqualTo(CALLER_TOKEN_1);

            JsonNode arguments = CapturedRequestUtils.toolCallArguments(summarizeSpendingCalls.get(0));
            assertThat(arguments.get("from").asText()).isEqualTo(from);
            assertThat(arguments.get("to").asText()).isEqualTo(to);
        }

        @Test
        @DisplayName("when no caller token is held for the turn - then it throws ExpenseRecordingFailedException")
        void whenNoCallerTokenHeld_thenThrowsExpenseRecordingFailedException() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.textResponse("irrelevant"));

            assertThatThrownBy(() -> adapter.record(
                            TEXT,
                            CATEGORY_GROUPINGS,
                            CATCH_ALL_GROUPING,
                            Optional.of(CurrencyCode.of("EUR")),
                            CURRENT_DATE,
                            Optional.empty()))
                    .isInstanceOf(ExpenseRecordingFailedException.class);
        }
    }
}
