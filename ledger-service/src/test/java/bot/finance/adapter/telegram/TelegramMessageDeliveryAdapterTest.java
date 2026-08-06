package bot.finance.adapter.telegram;

import static bot.finance.common.stubs.TelegramTestBot.DELIVERY_TOKEN;
import static bot.finance.common.stubs.TelegramTestBot.forToken;
import static bot.finance.common.stubs.TelegramTestBot.recordedAnswerCallbackQueries;
import static bot.finance.common.stubs.TelegramTestBot.recordedBotApiMethods;
import static bot.finance.common.stubs.TelegramTestBot.recordedEditMessageReplyMarkups;
import static bot.finance.common.stubs.TelegramTestBot.recordedSendMessages;
import static bot.finance.common.stubs.TelegramTestBot.replyMarkup;
import static bot.finance.common.stubs.WireMockStubs.telegramAcceptsAnswerCallbackQuery;
import static bot.finance.common.stubs.WireMockStubs.telegramAcceptsEditMessageReplyMarkup;
import static bot.finance.common.stubs.WireMockStubs.telegramAcceptsSendMessage;
import static bot.finance.common.stubs.WireMockStubs.telegramFailsAnswerCallbackQuery;
import static bot.finance.common.stubs.WireMockStubs.telegramFailsEditMessageReplyMarkup;
import static bot.finance.common.stubs.WireMockStubs.telegramFailsSendMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.ResolutionAcknowledgement;
import bot.finance.application.dto.ResolutionOutcome;
import bot.finance.application.dto.TurnReport;
import bot.finance.common.containers.WireMockSupport;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
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
 * pointed at the WireMock singleton and calls {@link TelegramMessageDeliveryAdapter#deliver(TurnReport)}
 * directly; nothing is mocked.
 */
class TelegramMessageDeliveryAdapterTest {

    private static final String CONVERSATION_ID = "777";
    private static final String INBOUND_MESSAGE_ID = "123";
    private static final String INTERACTION_ID = "callback-query-id-1";

    @AfterEach
    void tearDown() {
        WireMockSupport.SERVER.resetAll();
    }

    private static TurnReport recordedReportWithTwoSummaries() {
        return new TurnReport(
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
                                "Fuel",
                                "Auto",
                                "tank refill",
                                Optional.empty(),
                                new Money(6000, CurrencyCode.of("EUR")))),
                List.of(),
                MessageReference.newReference());
    }

    private static TurnReport nothingIdentifiedReport() {
        return new TurnReport(
                CONVERSATION_ID,
                INBOUND_MESSAGE_ID,
                ReportOutcome.NOTHING_IDENTIFIED,
                List.of(),
                List.of(),
                MessageReference.newReference());
    }

    private static ResolutionAcknowledgement acceptedAcknowledgement() {
        return new ResolutionAcknowledgement(
                CONVERSATION_ID, INBOUND_MESSAGE_ID, INTERACTION_ID, ResolutionOutcome.ACCEPTED, 1);
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
    @DisplayName("deliver(TurnReport)")
    class Deliver {

        @Test
        @DisplayName(
                "when a RECORDED report is delivered - then one sendMessage is addressed, threaded and rendered as the report says")
        void whenCalledWithRecordedReportAndSendMessageAccepted_thenExactlyOneSendMessageIsRecordedAsTheReportSays() {
            telegramAcceptsSendMessage(DELIVERY_TOKEN);
            TurnReport report = recordedReportWithTwoSummaries();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            adapter.deliver(report);

            List<LoggedRequest> sent = recordedSendMessages(DELIVERY_TOKEN);
            assertThat(sent).hasSize(1);
            LoggedRequest sendMessageRequest = sent.get(0);
            assertThat(sendMessageRequest.formParameter("chat_id").getValues()).containsExactly(CONVERSATION_ID);
            assertThat(sendMessageRequest.formParameter("text").getValues())
                    .containsExactly(TurnReportRenderer.render(report));
            assertThat(sendMessageRequest.formParameter("parse_mode").isPresent())
                    .isFalse();

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
            TurnReport report = recordedReportWithTwoSummaries();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            assertThatThrownBy(() -> adapter.deliver(report)).isInstanceOf(MessageDeliveryFailedException.class);
        }

        @Test
        @DisplayName(
                "when called over a bot pointed at an address that refuses the connection - then throws MessageDeliveryFailedException carrying the client exception as its cause")
        void
                whenBotIsPointedAtRefusingAddress_thenThrowsMessageDeliveryFailedExceptionCarryingClientExceptionAsCause() {
            TurnReport report = recordedReportWithTwoSummaries();
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

        @Test
        @DisplayName(
                "when a report carries proposals - then the sendMessage carries a Confirm and a Delete button for its reference")
        void
                whenCalledWithRecordedReportCarryingReference_thenSendMessageCarriesReplyMarkupWithConfirmAndDeleteButtons() {
            telegramAcceptsSendMessage(DELIVERY_TOKEN);
            TurnReport report = recordedReportWithTwoSummaries();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            adapter.deliver(report);

            List<LoggedRequest> sent = recordedSendMessages(DELIVERY_TOKEN);
            assertThat(sent).hasSize(1);
            JsonNode replyMarkup = replyMarkup(sent.get(0));
            assertThat(replyMarkup.get("inline_keyboard")).hasSize(1);
            JsonNode row = replyMarkup.get("inline_keyboard").get(0);
            assertThat(row).hasSize(2);
            assertThat(row.get(0).get("text").asText()).isEqualTo("Confirm");
            assertThat(row.get(0).get("callback_data").asText())
                    .isEqualTo(ProposalCallbackData.render(ProposalResolution.ACCEPT, report.reference()));
            assertThat(row.get(1).get("text").asText()).isEqualTo("Delete");
            assertThat(row.get(1).get("callback_data").asText())
                    .isEqualTo(ProposalCallbackData.render(ProposalResolution.DISCARD, report.reference()));
        }

        @Test
        @DisplayName("when a report carries no summaries - then the sendMessage carries no reply_markup")
        void whenCalledWithNothingIdentifiedReport_thenSendMessageCarriesNoReplyMarkupFormParam() {
            telegramAcceptsSendMessage(DELIVERY_TOKEN);
            TurnReport report = nothingIdentifiedReport();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            adapter.deliver(report);

            List<LoggedRequest> sent = recordedSendMessages(DELIVERY_TOKEN);
            assertThat(sent).hasSize(1);
            assertThat(sent.get(0).formParameter("reply_markup").isPresent()).isFalse();
        }
    }

    @Nested
    @DisplayName("acknowledge(ResolutionAcknowledgement)")
    class Acknowledge {

        @Test
        @DisplayName("when both calls are accepted - then the tap is answered and the report's buttons are cleared")
        void
                whenCalledWithAcceptedAcknowledgementAndBothCallsAccepted_thenBothCallsAreRecordedAsTheAcknowledgementSays() {
            telegramAcceptsAnswerCallbackQuery(DELIVERY_TOKEN);
            telegramAcceptsEditMessageReplyMarkup(DELIVERY_TOKEN);
            ResolutionAcknowledgement ack = acceptedAcknowledgement();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            adapter.acknowledge(ack);

            List<LoggedRequest> answers = recordedAnswerCallbackQueries(DELIVERY_TOKEN);
            assertThat(answers).hasSize(1);
            assertThat(answers.get(0).formParameter("callback_query_id").getValues())
                    .containsExactly(INTERACTION_ID);
            assertThat(answers.get(0).formParameter("text").getValues())
                    .containsExactly(ResolutionAcknowledgementRenderer.render(ack));

            List<LoggedRequest> edits = recordedEditMessageReplyMarkups(DELIVERY_TOKEN);
            assertThat(edits).hasSize(1);
            assertThat(edits.get(0).formParameter("chat_id").getValues()).containsExactly(CONVERSATION_ID);
            assertThat(edits.get(0).formParameter("message_id").getValues()).containsExactly(INBOUND_MESSAGE_ID);
            assertThat(edits.get(0).formParameter("reply_markup").isPresent()).isFalse();
        }

        @Test
        @DisplayName(
                "when both calls are accepted - then answerCallbackQuery is recorded before editMessageReplyMarkup")
        void
                whenCalledWithAcceptedAcknowledgement_thenAnswerCallbackQueryPrecedesEditMessageReplyMarkupInArrivalOrder() {
            telegramAcceptsAnswerCallbackQuery(DELIVERY_TOKEN);
            telegramAcceptsEditMessageReplyMarkup(DELIVERY_TOKEN);
            ResolutionAcknowledgement ack = acceptedAcknowledgement();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            adapter.acknowledge(ack);

            assertThat(recordedBotApiMethods(DELIVERY_TOKEN))
                    .containsExactly("answerCallbackQuery", "editMessageReplyMarkup");
        }

        @Test
        @DisplayName("when answerCallbackQuery is refused - then it throws and the buttons are still cleared")
        void
                whenAnswerCallbackQueryFailsAndEditMessageReplyMarkupAccepted_thenThrowsMessageDeliveryFailedExceptionAndEditIsStillRecorded() {
            telegramFailsAnswerCallbackQuery(DELIVERY_TOKEN, 400, "simulated answerCallbackQuery failure");
            telegramAcceptsEditMessageReplyMarkup(DELIVERY_TOKEN);
            ResolutionAcknowledgement ack = acceptedAcknowledgement();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            assertThatThrownBy(() -> adapter.acknowledge(ack)).isInstanceOf(MessageDeliveryFailedException.class);

            assertThat(recordedEditMessageReplyMarkups(DELIVERY_TOKEN)).hasSize(1);
        }

        @Test
        @DisplayName("when editMessageReplyMarkup is refused - then throws MessageDeliveryFailedException")
        void whenAnswerCallbackQueryAcceptedAndEditMessageReplyMarkupFails_thenThrowsMessageDeliveryFailedException() {
            telegramAcceptsAnswerCallbackQuery(DELIVERY_TOKEN);
            telegramFailsEditMessageReplyMarkup(DELIVERY_TOKEN, 400, "message is not modified");
            ResolutionAcknowledgement ack = acceptedAcknowledgement();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            assertThatThrownBy(() -> adapter.acknowledge(ack)).isInstanceOf(MessageDeliveryFailedException.class);
        }

        @Test
        @DisplayName("when both calls are refused - then the answerCallbackQuery failure is the one thrown")
        void
                whenBothCallsFailWithDistinguishableDescriptions_thenThrownExceptionMessageNamesAnswerCallbackQueryFailureDescription() {
            telegramFailsAnswerCallbackQuery(DELIVERY_TOKEN, 400, "simulated answerCallbackQuery failure");
            telegramFailsEditMessageReplyMarkup(DELIVERY_TOKEN, 400, "simulated editMessageReplyMarkup failure");
            ResolutionAcknowledgement ack = acceptedAcknowledgement();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            assertThatThrownBy(() -> adapter.acknowledge(ack))
                    .isInstanceOf(MessageDeliveryFailedException.class)
                    .hasMessageContaining("simulated answerCallbackQuery failure")
                    .hasMessageNotContaining("simulated editMessageReplyMarkup failure");
        }

        @Test
        @DisplayName(
                "when called with a null acknowledgement - then throws InvalidIncomingMessageException and sends nothing")
        void whenCalledWithNullAcknowledgement_thenThrowsInvalidIncomingMessageExceptionAndSendsNothing() {
            telegramAcceptsAnswerCallbackQuery(DELIVERY_TOKEN);
            telegramAcceptsEditMessageReplyMarkup(DELIVERY_TOKEN);
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            assertThatThrownBy(() -> adapter.acknowledge(null)).isInstanceOf(InvalidIncomingMessageException.class);

            assertThat(recordedAnswerCallbackQueries(DELIVERY_TOKEN)).isEmpty();
            assertThat(recordedEditMessageReplyMarkups(DELIVERY_TOKEN)).isEmpty();
        }

        @Test
        @DisplayName(
                "when the bot's address refuses the connection - then throws with the client exception as its cause")
        void whenBotAddressRefusesConnection_thenThrowsCarryingClientExceptionAsCause() {
            ResolutionAcknowledgement ack = acceptedAcknowledgement();
            TelegramMessageDeliveryAdapter adapter = adapterOver(refusingBot());

            assertThatThrownBy(() -> adapter.acknowledge(ack))
                    .isInstanceOf(MessageDeliveryFailedException.class)
                    .extracting(Throwable::getCause)
                    .isNotNull();
        }
    }
}
