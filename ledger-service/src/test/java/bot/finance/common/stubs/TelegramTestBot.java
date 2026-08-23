package bot.finance.common.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import bot.finance.common.containers.WireMockSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.pengrad.telegrambot.TelegramBot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The single home for pointing a real pengrad {@link TelegramBot} at the WireMock singleton, for the bot tokens
 * the tests use, for the scenarios that share one, and for reading back what the stub server recorded.
 *
 * <p>The token is part of every Bot API URL ({@code <apiUrl><token>/<method>}), so it partitions the stub
 * server's paths. A test that builds a bot of its own takes a token of its own; every system test runs against
 * the fully wired application, which is configured with {@link #PROFILE_DEFAULT_TOKEN}, and tells its scenario
 * from the next one by the {@link TelegramScenario} it owns.
 */
public final class TelegramTestBot {

    /**
     * The {@code telegram.bot.token} the {@code test} profile configures, and therefore the one every system
     * test's poll loop and every system test's stubs use.
     */
    public static final String PROFILE_DEFAULT_TOKEN = "default-test-token";

    /**
     * Token owned by {@code TelegramUpdateListenerTest}.
     */
    public static final String LISTENER_TOKEN = "listener-test-token";

    /**
     * Token owned by {@code TelegramLongPollingSubscriberTest}.
     */
    public static final String SUBSCRIBER_TOKEN = "subscriber-test-token";

    /**
     * Token owned by {@code TelegramMessageDeliveryAdapterTest}.
     */
    public static final String DELIVERY_TOKEN = "delivery-test-token";

    /**
     * What tells one poll-loop scenario from another: the update it is delivered as, the Telegram user who sends
     * it, the conversation it arrives in, and — for a tap — the callback query that carries it.
     *
     * <p>The constants below are the whole allocation, so that no two scenarios sharing an id is something a
     * reader can check by looking at one list. The user matters beyond the poll loop: a user's first message
     * seeds their entire category tree, and a user who already exists is not seeded again, so two scenarios on
     * one id would depend on which ran first against the shared database.
     */
    public record TelegramScenario(int updateId, long userId, long chatId) {

        public String userExternalId() {
            return String.valueOf(userId);
        }

        public String conversationId() {
            return String.valueOf(chatId);
        }

        /** The {@code offset} pengrad polls with once it has confirmed this scenario's batch. */
        public String nextOffset() {
            return String.valueOf(updateId + 1);
        }

        /** A {@code callback_query.id} no other scenario's tap carries. */
        public String callbackQueryId() {
            return "callback-query-%d".formatted(updateId);
        }
    }

    /** Scenario owned by {@code ReceiveTelegramMessageSystemTest}. */
    public static final TelegramScenario RECEIVE_MESSAGE = new TelegramScenario(101, 1001, 2001);

    /** Scenario owned by {@code SummarizeSpendingReplySystemTest}. */
    public static final TelegramScenario SUMMARIZE_SPENDING = new TelegramScenario(201, 1002, 2002);

    /** Scenario owned by {@code ResolveProposalsSystemTest}. */
    public static final TelegramScenario RESOLVE_PROPOSALS = new TelegramScenario(301, 1003, 2003);

    /** Scenario owned by {@code ResolveUnknownProposalsSystemTest}. */
    public static final TelegramScenario RESOLVE_UNKNOWN_PROPOSALS = new TelegramScenario(401, 1004, 2004);

    /** Scenario owned by {@code HandleIncomingMessageFailureSystemTest}. */
    public static final TelegramScenario HANDLE_MESSAGE_FAILURE = new TelegramScenario(501, 1005, 2005);

    /** Scenario owned by {@code TelegramPollFailureRecoverySystemTest}. */
    public static final TelegramScenario POLL_RECOVERY = new TelegramScenario(601, 1006, 2006);

    /**
     * Scenario owned by {@code AcceptExpensesSystemTest}. Nothing is polled here — the clearing it asserts on is
     * dispatched off the request thread, and the conversation is what tells it from another class's traffic.
     */
    public static final TelegramScenario ACCEPT_EXPENSES = new TelegramScenario(701, 1007, 2007);

    /** Scenario owned by {@code PipelineMetersSystemTest}. */
    public static final TelegramScenario PIPELINE_METERS = new TelegramScenario(801, 1008, 2008);

    /**
     * Scenario owned by {@code ReceiveTelegramMessageSystemTest}'s default-currency turn: a sender who already
     * exists, with a preference row set before the turn runs.
     */
    public static final TelegramScenario RECEIVE_MESSAGE_WITH_DEFAULT_CURRENCY = new TelegramScenario(901, 1009, 2009);

    private static final long UPDATE_LISTENER_SLEEP_MILLIS = 50L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TelegramTestBot() {}

    /**
     * The token-scoped path pengrad posts {@code getUpdates} to: {@code /bot<token>/getUpdates}. Stub
     * registration and request verification both go through this, never a hand-written path.
     */
    public static String getUpdatesPath(String token) {
        return "/bot%s/getUpdates".formatted(token);
    }

    /**
     * The token-scoped path pengrad posts {@code sendMessage} to: {@code /bot<token>/sendMessage}. Stub
     * registration and request verification both go through this, never a hand-written path.
     */
    public static String sendMessagePath(String token) {
        return "/bot%s/sendMessage".formatted(token);
    }

    /**
     * The token-scoped path pengrad posts {@code answerCallbackQuery} to: {@code /bot<token>/answerCallbackQuery}.
     * Stub registration and request verification both go through this, never a hand-written path.
     */
    public static String answerCallbackQueryPath(String token) {
        return "/bot%s/answerCallbackQuery".formatted(token);
    }

    /**
     * The token-scoped path pengrad posts {@code editMessageReplyMarkup} to:
     * {@code /bot<token>/editMessageReplyMarkup}. Stub registration and request verification both go through
     * this, never a hand-written path.
     */
    public static String editMessageReplyMarkupPath(String token) {
        return "/bot%s/editMessageReplyMarkup".formatted(token);
    }

    /**
     * A {@link TelegramBot} whose {@code apiUrl} points at the WireMock singleton, polling at a short sleep
     * interval so an asynchronous assertion does not have to wait long.
     */
    public static TelegramBot forToken(String token) {
        return new TelegramBot.Builder(token)
                .apiUrl(WireMockSupport.baseUrl() + "/bot")
                .updateListenerSleep(UPDATE_LISTENER_SLEEP_MILLIS)
                .build();
    }

    /**
     * Every {@code getUpdates} poll the stub server recorded for this token.
     */
    public static List<LoggedRequest> recordedPolls(String token) {
        return WireMockSupport.SERVER.findAll(postRequestedFor(urlPathEqualTo(getUpdatesPath(token))));
    }

    /**
     * The recorded {@code getUpdates} polls carrying the given {@code offset} form param. Only pengrad's own loop
     * sets that param, and only after the listener confirmed a batch, so a non-empty result is proof the batch
     * was consumed.
     */
    public static List<LoggedRequest> recordedPollsWithOffset(String token, String offset) {
        return WireMockSupport.SERVER.findAll(
                postRequestedFor(urlPathEqualTo(getUpdatesPath(token))).withFormParam("offset", equalTo(offset)));
    }

    /**
     * Every {@code sendMessage} the stub server recorded for this token.
     */
    public static List<LoggedRequest> recordedSendMessages(String token) {
        return WireMockSupport.SERVER.findAll(postRequestedFor(urlPathEqualTo(sendMessagePath(token))));
    }

    /**
     * The recorded {@code sendMessage} calls addressed to this scenario's conversation.
     *
     * <p>A turn still running when the next test starts writes into that test's journal, and the conversation is
     * what tells the two apart once every scenario shares one token and one poll loop.
     */
    public static List<LoggedRequest> recordedSendMessagesFor(String token, TelegramScenario scenario) {
        return recordedFor(sendMessagePath(token), "chat_id", scenario.conversationId());
    }

    /**
     * The recorded {@code editMessageReplyMarkup} calls addressed to this scenario's conversation.
     */
    public static List<LoggedRequest> recordedEditMessageReplyMarkupsFor(String token, TelegramScenario scenario) {
        return recordedFor(editMessageReplyMarkupPath(token), "chat_id", scenario.conversationId());
    }

    /**
     * The recorded {@code answerCallbackQuery} calls answering this scenario's own tap.
     */
    public static List<LoggedRequest> recordedAnswerCallbackQueriesFor(String token, TelegramScenario scenario) {
        return recordedFor(answerCallbackQueryPath(token), "callback_query_id", scenario.callbackQueryId());
    }

    private static List<LoggedRequest> recordedFor(String path, String formParameter, String value) {
        return WireMockSupport.SERVER.findAll(
                postRequestedFor(urlPathEqualTo(path)).withFormParam(formParameter, equalTo(value)));
    }

    /**
     * Every {@code answerCallbackQuery} the stub server recorded for this token.
     */
    public static List<LoggedRequest> recordedAnswerCallbackQueries(String token) {
        return WireMockSupport.SERVER.findAll(postRequestedFor(urlPathEqualTo(answerCallbackQueryPath(token))));
    }

    /**
     * Every {@code editMessageReplyMarkup} the stub server recorded for this token.
     */
    public static List<LoggedRequest> recordedEditMessageReplyMarkups(String token) {
        return WireMockSupport.SERVER.findAll(postRequestedFor(urlPathEqualTo(editMessageReplyMarkupPath(token))));
    }

    /**
     * The Bot API method names the stub server received for this token, in arrival order — the last path segment
     * of every {@code /bot<token>/<method>} request.
     */
    public static List<String> recordedBotApiMethods(String token) {
        return WireMockSupport.SERVER.findAll(postRequestedFor(urlPathMatching("/bot%s/.*".formatted(token)))).stream()
                .map(request -> request.getUrl().substring(request.getUrl().lastIndexOf('/') + 1))
                .toList();
    }

    /**
     * The {@code reply_parameters} form param of a recorded {@code sendMessage}, parsed — pengrad sends it as a
     * JSON document inside a form field, so reading {@code message_id} or {@code allow_sending_without_reply} off
     * it means parsing rather than a string comparison.
     */
    public static JsonNode replyParameters(LoggedRequest sendMessageRequest) {
        return formParameterAsJson(sendMessageRequest, "reply_parameters");
    }

    /**
     * The {@code reply_markup} form param of a recorded {@code sendMessage}, parsed — pengrad sends it as a JSON
     * document inside a form field, the way it sends {@code reply_parameters}.
     */
    public static JsonNode replyMarkup(LoggedRequest sendMessageRequest) {
        return formParameterAsJson(sendMessageRequest, "reply_markup");
    }

    private static JsonNode formParameterAsJson(LoggedRequest request, String name) {
        String json = request.formParameter(name).getValues().get(0);
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse %s: %s".formatted(name, json), e);
        }
    }
}
