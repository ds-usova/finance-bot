package bot.finance.system;

import static bot.finance.common.stubs.TelegramTestBot.recordedSendMessagesFor;
import static bot.finance.common.stubs.TelegramTestBot.replyParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.IncomingTurnEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.fixtures.TelegramFixtures;
import bot.finance.common.fixtures.TurnOperationCalls;
import bot.finance.common.rows.IncomingTurnRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.common.stubs.WireMockStubs;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.ledger.adapter.grpc.v1.EndTurnResponse;
import bot.finance.ledger.adapter.grpc.v1.TurnEnd;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;

/**
 * Drives {@code LedgerTurnEndService.EndTurn} on the ledger's gRPC port end to end against the fully wired
 * application.
 */
class EndTurnSystemTest extends AbstractSystemTest {

    private static final String TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;

    private static final TelegramTestBot.TelegramScenario SCENARIO = TelegramTestBot.END_TURN;

    private static final String MESSAGE_TEXT = "lunch 12 euro";
    private static final String EXPECTED_TEXT = "No expense was identified in that message.";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Autowired
    private GrpcServerLifecycle grpcServerLifecycle;

    @BeforeEach
    void stubTelegram() {
        WireMockStubs.telegramAcceptsSendMessage(TOKEN);
    }

    private long storedHandedOverTurn(long userId, String inboundMessageId) {
        Instant now = Instant.now();
        return IncomingTurnRowUtils.storedTurnId(
                jdbcAggregateTemplate,
                userId,
                SCENARIO.conversationId(),
                inboundMessageId,
                MESSAGE_TEXT,
                now.minusSeconds(10),
                now.minusSeconds(5),
                now.minusSeconds(5));
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a turn is ended acted on - then the chat gets the nothing-identified report and the row is "
                + "gone")
        void whenTurnIsEndedActedOn_thenChatGetsNothingIdentifiedReportAndRowIsGone() {
            // given: a stored person, a turn of theirs handed over, and a token minted for them and that message
            String inboundMessageId = String.valueOf(TelegramFixtures.MESSAGE_ID);
            long userId = UserRowUtils.storedUserId(userEntityRepository, SCENARIO.userExternalId());
            long turnId = storedHandedOverTurn(userId, inboundMessageId);
            String token = McpTokens.tokenFor(
                    accessTokenMinter, userId, IncomingMessageId.of(SCENARIO.conversationId(), inboundMessageId));

            // when: EndTurn is called on the gRPC port, acted on
            EndTurnResponse answer =
                    TurnOperationCalls.endTurn(grpcServerLifecycle.getPort(), token, TurnEnd.TURN_END_ACTED_ON);

            // then: the call answers empty
            assertThat(answer).as("the call's answer").isEqualTo(EndTurnResponse.getDefaultInstance());

            // then: the chat receives the nothing-identified report, threaded on the message
            await("a sendMessage report is recorded for the conversation")
                    .atMost(TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(recordedSendMessagesFor(TOKEN, SCENARIO))
                            .as("sendMessage requests recorded for the conversation")
                            .hasSize(1));
            LoggedRequest report = recordedSendMessagesFor(TOKEN, SCENARIO).get(0);
            assertThat(report.formParameter("text").getValues())
                    .as("sendMessage text form param")
                    .containsExactly(EXPECTED_TEXT);
            assertThat(replyParameters(report).get("message_id").asText())
                    .as("sendMessage reply_parameters message_id")
                    .isEqualTo(inboundMessageId);

            // then: the turn's row is gone
            assertThat(IncomingTurnRowUtils.turnRow(jdbcAggregateTemplate, turnId))
                    .as("the turn's row")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when a turn is ended with another person's token for that message - then nothing is reported "
                + "and the row remains")
        void whenTurnIsEndedWithAnotherPersonsToken_thenNothingIsReportedAndRowRemains() {
            // given: a turn of a stored person, and a token another stored person was minted for that message
            String inboundMessageId = "2";
            long ownerId = UserRowUtils.storedUserId(userEntityRepository, "end-turn-unhappy-owner");
            long otherId = UserRowUtils.storedUserId(userEntityRepository, "end-turn-unhappy-other");
            long turnId = storedHandedOverTurn(ownerId, inboundMessageId);
            String token = McpTokens.tokenFor(
                    accessTokenMinter, otherId, IncomingMessageId.of(SCENARIO.conversationId(), inboundMessageId));

            try {
                // when: EndTurn is called with that token
                EndTurnResponse answer =
                        TurnOperationCalls.endTurn(grpcServerLifecycle.getPort(), token, TurnEnd.TURN_END_ACTED_ON);

                // then: the call answers empty
                assertThat(answer).as("the call's answer").isEqualTo(EndTurnResponse.getDefaultInstance());

                // then: no report is sent and the row remains
                await("no report is sent for the conversation")
                        .during(Duration.ofSeconds(2))
                        .atMost(TIMEOUT)
                        .pollInterval(POLL_INTERVAL)
                        .untilAsserted(() -> assertThat(recordedSendMessagesFor(TOKEN, SCENARIO))
                                .as("sendMessage requests recorded for the conversation")
                                .isEmpty());
                List<IncomingTurnEntity> rows = IncomingTurnRowUtils.turnRowsFor(jdbcAggregateTemplate, ownerId);
                assertThat(rows).as("the owner's turn rows").hasSize(1);
                assertThat(rows.get(0).id()).as("the turn's row").isEqualTo(turnId);
            } finally {
                jdbcAggregateTemplate.deleteById(turnId, IncomingTurnEntity.class);
            }
        }
    }
}
