package bot.finance.adapter.telegram;

import static bot.finance.common.TelegramTestBot.DELIVERY_TOKEN;
import static bot.finance.common.TelegramTestBot.forToken;
import static bot.finance.common.TelegramTestBot.recordedSendMessages;
import static bot.finance.common.WireMockStubs.telegramAcceptsSendMessage;
import static bot.finance.common.WireMockStubs.telegramFailsSendMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.application.dto.ProposalReport;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.common.TelegramTestBot;
import bot.finance.common.containers.WireMockSupport;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.pengrad.telegrambot.TelegramBot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the outbound Telegram adapter. Its protocol is the Telegram Bot API over HTTP, not an
 * endpoint this service exposes, so the test wires only the adapter under test over a real {@link TelegramBot}
 * pointed at the WireMock singleton and calls {@link TelegramMessageDeliveryAdapter#deliver(ProposalReport)}
 * directly; nothing is mocked.
 */
class TelegramMessageDeliveryAdapterTest {

    private static final String CONVERSATION_ID = "777";
    private static final String INBOUND_MESSAGE_ID = "123";

    @AfterEach
    void tearDown() {
        WireMockSupport.SERVER.resetAll();
    }

    private static ProposalReport recordedReportWithTwoSummaries() {
        return new ProposalReport(
                CONVERSATION_ID,
                INBOUND_MESSAGE_ID,
                ReportOutcome.RECORDED,
                List.of(
                        new ProposalSummary(
                                "Groceries",
                                "Food",
                                "weekly shop",
                                Optional.of("Rewe"),
                                new Money(4230, CurrencyCode.of("EUR"))),
                        new ProposalSummary(
                                "Fuel", "Auto", "tank refill", Optional.empty(), new Money(6000, CurrencyCode.of("EUR")))));
    }

    private static TelegramMessageDeliveryAdapter adapterOver(TelegramBot bot) {
        return new TelegramMessageDeliveryAdapter(bot, new Slf4jLoggerFactory());
    }

    /**
     * A bot pointed at a port nothing listens on, for the connection-refused scenario: the port is opened and
     * immediately closed, so it is free but unreachable for the lifetime of the test.
     */
    private static TelegramBot refusingBot() {
        int unreachablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unreachablePort = socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new TelegramBot.Builder(DELIVERY_TOKEN)
                .apiUrl("http://localhost:" + unreachablePort + "/bot")
                .build();
    }

    @Nested
    @DisplayName("deliver(ProposalReport)")
    class Deliver {

        @Test
        @DisplayName(
                "when called with a RECORDED report over a bot WireMock accepts sendMessage for - then exactly one sendMessage is recorded, addressed and replying as the report says, rendered as ProposalReportUtils.render() says, with no parse_mode")
        void whenCalledWithRecordedReportAndSendMessageAccepted_thenExactlyOneSendMessageIsRecordedAsTheReportSays() {
            telegramAcceptsSendMessage(DELIVERY_TOKEN);
            ProposalReport report = recordedReportWithTwoSummaries();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            adapter.deliver(report);

            List<LoggedRequest> sent = recordedSendMessages(DELIVERY_TOKEN);
            assertThat(sent).hasSize(1);
            LoggedRequest sendMessageRequest = sent.get(0);
            assertThat(sendMessageRequest.formParameter("chat_id").getValues()).containsExactly(CONVERSATION_ID);
            assertThat(sendMessageRequest.formParameter("text").getValues())
                    .containsExactly(ProposalReportUtils.render(report));
            assertThat(sendMessageRequest.formParameter("parse_mode").isPresent()).isFalse();

            JsonNode replyParameters = TelegramTestBot.replyParameters(sendMessageRequest);
            assertThat(replyParameters.get("message_id").asText()).isEqualTo(INBOUND_MESSAGE_ID);
            assertThat(replyParameters.get("allow_sending_without_reply").asBoolean())
                    .isTrue();
        }

        @Test
        @DisplayName(
                "when called over a bot WireMock answers sendMessage with a non-OK envelope for - then throws MessageDeliveryFailedException")
        void whenSendMessageAnsweredWithNonOkEnvelope_thenThrowsMessageDeliveryFailedException() {
            telegramFailsSendMessage(DELIVERY_TOKEN, 400, "simulated sendMessage failure");
            ProposalReport report = recordedReportWithTwoSummaries();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            assertThatThrownBy(() -> adapter.deliver(report)).isInstanceOf(MessageDeliveryFailedException.class);
        }

        @Test
        @DisplayName(
                "when called over a bot pointed at an address that refuses the connection - then throws MessageDeliveryFailedException carrying the client exception as its cause")
        void whenBotIsPointedAtRefusingAddress_thenThrowsMessageDeliveryFailedExceptionCarryingClientExceptionAsCause() {
            ProposalReport report = recordedReportWithTwoSummaries();
            TelegramMessageDeliveryAdapter adapter = adapterOver(refusingBot());

            assertThatThrownBy(() -> adapter.deliver(report))
                    .isInstanceOf(MessageDeliveryFailedException.class)
                    .extracting(Throwable::getCause)
                    .isNotNull();
        }

        @Test
        @DisplayName("when called with a null report - then throws InvalidIncomingMessageException and sends nothing")
        void whenCalledWithNullReport_thenThrowsInvalidIncomingMessageExceptionAndSendsNothing() {
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            assertThatThrownBy(() -> adapter.deliver(null)).isInstanceOf(InvalidIncomingMessageException.class);

            assertThat(recordedSendMessages(DELIVERY_TOKEN)).isEmpty();
        }
    }
}
