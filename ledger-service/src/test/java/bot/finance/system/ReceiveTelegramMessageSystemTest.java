package bot.finance.system;

import static bot.finance.common.TelegramTestBot.recordedPolls;
import static bot.finance.common.TelegramTestBot.recordedPollsWithOffset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.KnownCategory;
import bot.finance.application.port.UserRepository;
import bot.finance.application.usecase.HandleIncomingMessageUseCase;
import bot.finance.common.AbstractSystemTest;
import bot.finance.common.LogCapture;
import bot.finance.common.TelegramFixtures;
import bot.finance.common.TelegramTestBot;
import bot.finance.common.WireMockStubs;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.grpc.Metadata;
import java.text.ParseException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * The bot token below is what isolates this class: a differing property defeats Spring's context cache, so the
 * class gets its own context, a poll loop starting at offset 0, and a {@code /bot<token>/getUpdates} path no
 * other class's poller reaches.
 */
@TestPropertySource(properties = "telegram.bot.token=" + TelegramTestBot.RECEIVE_MESSAGE_TOKEN)
class ReceiveTelegramMessageSystemTest extends AbstractSystemTest {

    private static final String TOKEN = TelegramTestBot.RECEIVE_MESSAGE_TOKEN;

    private static final int UPDATE_ID = 42;
    private static final long CHAT_ID = 555L;
    private static final String MESSAGE_TEXT = "lunch 12 euro";
    private static final String CONVERSATION_ID = "555";
    private static final String NEXT_OFFSET = "43";

    /** Every category {@link Category#defaults()} gives a new user: the groupings and their children alike. */
    private static final int EXPECTED_CATEGORY_COUNT =
            Category.defaults().size() + Category.defaults().stream().mapToInt(group -> group.children().size()).sum();

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Autowired
    private UserRepository userRepository;

    private LogCapture logCapture;

    /**
     * The order below is load-bearing: the poll loop is already running, so the appender must be attached before
     * the update-bearing stub exists, or the loop consumes the update and logs it into a logger with no appender.
     * The catch-all comes before it so the loop never sees a bare 404.
     */
    @BeforeEach
    void stubTelegram() {
        logCapture = LogCapture.attachedTo(HandleIncomingMessageUseCase.class);
        WireMockStubs.telegramReturnsNoUpdates(TOKEN);
        WireMockStubs.telegramReturnsOnFirstPoll(
                TOKEN,
                TelegramFixtures.updatesResponse(TelegramFixtures.textMessageUpdate(UPDATE_ID, CHAT_ID, CHAT_ID, MESSAGE_TEXT)));
    }

    @AfterEach
    void detachLogCapture() {
        logPollLoopState();
        logCapture.close();
    }

    private void logPollLoopState() {
        List<String> polls = recordedPolls(TOKEN).stream()
                .map(LoggedRequest::getBodyAsString)
                .toList();

        log.debug("getUpdates requests recorded for token {}: {}", TOKEN, polls);
        log.debug("messages captured from {}: {}", HandleIncomingMessageUseCase.class.getName(), logCapture.messages());
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName(
                "when the running poll loop picks up a text message update - then the batch is confirmed, the AI "
                        + "connector receives the message text, the user's known categories with their parent "
                        + "names, and a bearer token whose sub is the conversation id, and the use case's info "
                        + "line names the conversation without the text")
        void whenRunningPollLoopPicksUpTextMessageUpdate_thenBatchIsConfirmedAndMessageIsPrinted() throws ParseException {
            await("the batch is confirmed with a follow-up getUpdates carrying offset=" + NEXT_OFFSET)
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(recordedPollsWithOffset(TOKEN, NEXT_OFFSET))
                            .as("follow-up getUpdates polls carrying offset=%s", NEXT_OFFSET)
                            .isNotEmpty());

            await("the AI connector receives an extraction request")
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(GrpcStubServer.lastExtractionRequest())
                            .as("last ExtractIntentsRequest received by the stub AI connector")
                            .isNotNull());

            User storedUser = userRepository
                    .findByExternalId(CONVERSATION_ID)
                    .orElseThrow(() -> new AssertionError("the turn stored no user for " + CONVERSATION_ID));
            Integer storedCategoryCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM category WHERE user_id = ?",
                    Integer.class,
                    storedUser.id().orElseThrow());
            assertThat(storedCategoryCount)
                    .as("the categories the first message created for this conversation")
                    .isEqualTo(EXPECTED_CATEGORY_COUNT);

            ExtractIntentsRequest request = GrpcStubServer.lastExtractionRequest();
            assertThat(request.getText()).as("extraction request text").isEqualTo(MESSAGE_TEXT);

            List<KnownCategory> expectedKnownCategories = Category.defaults().stream()
                    .flatMap(group -> group.children().stream()
                            .map(child -> KnownCategory.newBuilder()
                                    .setName(child.name())
                                    .setParentName(group.name())
                                    .build()))
                    .toList();
            assertThat(request.getKnownCategoriesList())
                    .as("extraction request's known categories")
                    .containsExactlyInAnyOrderElementsOf(expectedKnownCategories);

            Metadata metadata = GrpcStubServer.lastExtractionMetadata();
            assertThat(metadata).as("metadata received by the stub AI connector").isNotNull();
            String authorizationHeader = metadata.get(AUTHORIZATION_KEY);
            assertThat(authorizationHeader).as("authorization metadata").startsWith("Bearer ");
            String token = authorizationHeader.substring("Bearer ".length());
            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
            assertThat(claims.getSubject()).as("jwt sub claim").isEqualTo(CONVERSATION_ID);

            await("the use case logs an info line naming the conversation without the message text")
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(logCapture.messages())
                            .as("messages logged by the use case")
                            .anySatisfy(message -> assertThat(message)
                                    .contains(CONVERSATION_ID)
                                    .doesNotContain(MESSAGE_TEXT)));
        }
    }
}
