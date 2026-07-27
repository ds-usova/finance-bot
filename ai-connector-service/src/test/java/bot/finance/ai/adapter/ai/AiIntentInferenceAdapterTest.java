package bot.finance.ai.adapter.ai;

import bot.finance.ai.application.dto.RawIntent;
import bot.finance.ai.common.AiAdapterTest;
import bot.finance.ai.common.ChatCompletionFixtures;
import bot.finance.ai.common.JsonUtils;
import bot.finance.ai.common.WireMockStubs;
import bot.finance.ai.common.WireMockSupport;
import bot.finance.ai.domain.exception.IntentInferenceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AiAdapterTest
class AiIntentInferenceAdapterTest {

    private static final String SYSTEM_PROMPT_RESOURCE = "prompts/extract-intents.st";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private AiIntentInferenceAdapter adapter;

    @Value("${spring.ai.openai.chat.model}")
    private String configuredModel;

    @AfterEach
    void tearDown() {
        WireMockSupport.SERVER.resetAll();
    }

    private static List<LoggedRequest> capturedRequests() {
        return WireMockSupport.SERVER.findAll(
                postRequestedFor(urlPathEqualTo(WireMockStubs.CHAT_COMPLETIONS_PATH)));
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
     * Spring AI's default structured-output converter appends the JSON schema as fenced text inside the last
     * user message, rather than via the provider's native {@code response_format} field — this is what wiring
     * this test against a real WireMock request first proves.
     */
    private static JsonNode embeddedSchema(String userMessageContent) throws JsonProcessingException {
        int fenceAfterLabel = userMessageContent.indexOf("```", userMessageContent.indexOf("adhere to:"));
        int schemaStart = fenceAfterLabel + "```".length();
        int schemaEnd = userMessageContent.indexOf("```", schemaStart);
        return MAPPER.readTree(userMessageContent.substring(schemaStart, schemaEnd));
    }

    @Nested
    @DisplayName("infer()")
    class Infer {

        @Test
        @DisplayName("when the provider returns one extracted expense entry - then returns a single RawIntent carrying its fields verbatim, amount still a decimal string")
        void whenProviderReturnsOneExpenseEntry_thenReturnsSingleVerbatimRawIntent() {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson(
                    ChatCompletionFixtures.intentEntry()
                            .target("expense")
                            .operation("create")
                            .categoryName("Food")
                            .amount("15.00")
                            .currency("EUR")
                            .description("lunch")
                            .build()));

            List<RawIntent> result = adapter.infer("spent 15 euros on lunch", List.of("Food", "Other"));

            assertThat(result).containsExactly(
                    new RawIntent("expense", "create", "Food", null, "15.00", "EUR", "lunch"));
        }

        @Test
        @DisplayName("when the provider returns three extracted entries - then returns three RawIntents in the same order the provider listed them")
        void whenProviderReturnsThreeEntries_thenReturnsThreeRawIntentsInOrder() {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson(
                    ChatCompletionFixtures.intentEntry()
                            .target("category")
                            .operation("create")
                            .categoryName("Groceries")
                            .build(),
                    ChatCompletionFixtures.intentEntry()
                            .target("expense")
                            .operation("create")
                            .categoryName("Groceries")
                            .amount("20.00")
                            .currency("EUR")
                            .description("milk")
                            .build(),
                    ChatCompletionFixtures.intentEntry()
                            .target("category")
                            .operation("update")
                            .categoryName("Old")
                            .newCategoryName("New")
                            .build()));

            List<RawIntent> result = adapter.infer(
                    "create a Groceries category, spend 20 euros on milk in it, rename Old to New", List.of("Other"));

            assertThat(result).containsExactly(
                    new RawIntent("category", "create", "Groceries", null, null, null, null),
                    new RawIntent("expense", "create", "Groceries", null, "20.00", "EUR", "milk"),
                    new RawIntent("category", "update", "Old", "New", null, null, null));
        }

        @Test
        @DisplayName("when the provider returns an entry with several fields absent - then those RawIntent fields are null and no exception is thrown")
        void whenEntryHasSeveralFieldsAbsent_thenThoseRawIntentFieldsAreNull() {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson(
                    ChatCompletionFixtures.intentEntry()
                            .target("expense")
                            .operation("read")
                            .build()));

            List<RawIntent> result = adapter.infer("what did I spend on last", List.of("Food", "Other"));

            assertThat(result).containsExactly(
                    new RawIntent("expense", "read", null, null, null, null, null));
        }

        @Test
        @DisplayName("when the provider returns an empty intents array - then returns an empty list, not null and not an exception")
        void whenProviderReturnsEmptyIntentsArray_thenReturnsEmptyList() {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson());

            List<RawIntent> result = adapter.infer("good morning", List.of("Food", "Other"));

            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("when infer() is called - then the request names the configured model and the user's text, and its JSON schema declares intents as an array whose entries' amount is a string")
        void whenInferIsCalled_thenRequestNamesModelAndTextAndSchemaDeclaresAmountAsString() throws JsonProcessingException {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson(
                    ChatCompletionFixtures.intentEntry()
                            .target("category")
                            .operation("create")
                            .categoryName("Travel")
                            .build()));

            adapter.infer("create a Travel category", List.of("Food", "Travel"));

            List<LoggedRequest> requests = capturedRequests();
            assertThat(requests).hasSize(1);
            JsonNode body = requestBody(requests.get(0));
            assertThat(body.get("model").asText()).isEqualTo(configuredModel);
            assertThat(messageContent(body, "user")).contains("create a Travel category");
            JsonNode schema = embeddedSchema(messageContent(body, "user"));
            assertThat(schema.at("/properties/intents/type").asText()).isEqualTo("array");
            assertThat(schema.at("/properties/intents/items/properties/amount/type").asText()).isEqualTo("string");
        }

        @Test
        @DisplayName("when known categories are Food and Travel - then the request carries both names in the user message, and the system message is the static instructions with no category in it")
        void whenKnownCategoriesGiven_thenUserMessageCarriesThemAndSystemMessageHasNone() throws JsonProcessingException {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson(
                    ChatCompletionFixtures.intentEntry()
                            .target("category")
                            .operation("create")
                            .categoryName("Travel")
                            .build()));

            adapter.infer("create a Travel category", List.of("Food", "Travel"));

            JsonNode body = requestBody(capturedRequests().get(0));
            String userMessage = messageContent(body, "user");
            assertThat(userMessage).contains("Food").contains("Travel");
            String systemMessage = messageContent(body, "system");
            assertThat(systemMessage).isEqualTo(JsonUtils.readJsonResourceAsString(SYSTEM_PROMPT_RESOURCE));
            assertThat(systemMessage).doesNotContain("Food").doesNotContain("Travel");
        }

        @Test
        @DisplayName("when infer() is called once with Food then again with Travel - then the second request carries Travel and not Food")
        void whenCalledTwiceWithDifferentCategories_thenSecondRequestCarriesOnlyItsOwnCategories() throws JsonProcessingException {
            WireMockStubs.stubChatCompletion(ChatCompletionFixtures.extractedIntentsJson(
                    ChatCompletionFixtures.intentEntry()
                            .target("category")
                            .operation("create")
                            .categoryName("Placeholder")
                            .build()));

            adapter.infer("create a category", List.of("Food"));
            adapter.infer("create a category", List.of("Travel"));

            List<LoggedRequest> requests = capturedRequests();
            assertThat(requests).hasSize(2);
            JsonNode secondBody = requestBody(requests.get(1));
            String secondUserMessage = messageContent(secondBody, "user");
            assertThat(secondUserMessage).contains("Travel").doesNotContain("Food");
        }

        @Test
        @DisplayName("when the provider responds 500 - then throws IntentInferenceException, not a Spring AI or HTTP client exception")
        void whenProviderRespondsServerError_thenThrowsIntentInferenceException() {
            WireMockStubs.stubChatCompletionServerError();

            assertThatThrownBy(() -> adapter.infer("spent 15 euros on lunch", List.of("Food", "Other")))
                    .isInstanceOf(IntentInferenceException.class);
        }

        @Test
        @DisplayName("when the provider responds 200 with a body Spring AI cannot parse - then throws IntentInferenceException")
        void whenProviderRespondsUnparseableBody_thenThrowsIntentInferenceException() {
            WireMockStubs.stubMalformedChatCompletion();

            assertThatThrownBy(() -> adapter.infer("spent 15 euros on lunch", List.of("Food", "Other")))
                    .isInstanceOf(IntentInferenceException.class);
        }

    }

}
