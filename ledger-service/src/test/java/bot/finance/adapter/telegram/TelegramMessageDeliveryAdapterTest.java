package bot.finance.adapter.telegram;

import static bot.finance.common.TelegramTestBot.DELIVERY_TOKEN;
import static bot.finance.common.TelegramTestBot.forToken;
import static bot.finance.common.TelegramTestBot.recordedAnswerCallbackQueries;
import static bot.finance.common.TelegramTestBot.recordedBotApiMethods;
import static bot.finance.common.TelegramTestBot.recordedEditMessageReplyMarkups;
import static bot.finance.common.TelegramTestBot.recordedSendMessages;
import static bot.finance.common.TelegramTestBot.replyMarkup;
import static bot.finance.common.WireMockStubs.telegramAcceptsAnswerCallbackQuery;
import static bot.finance.common.WireMockStubs.telegramAcceptsEditMessageReplyMarkup;
import static bot.finance.common.WireMockStubs.telegramAcceptsSendMessage;
import static bot.finance.common.WireMockStubs.telegramFailsAnswerCallbackQuery;
import static bot.finance.common.WireMockStubs.telegramFailsEditMessageReplyMarkup;
import static bot.finance.common.WireMockStubs.telegramFailsSendMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.application.dto.ProposalReport;
import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.ResolutionAcknowledgement;
import bot.finance.application.dto.ResolutionOutcome;
import bot.finance.common.TelegramTestBot;
import bot.finance.common.containers.WireMockSupport;
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
 * pointed at the WireMock singleton and calls {@link TelegramMessageDeliveryAdapter#deliver(ProposalReport)}
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
                                "Fuel",
                                "Auto",
                                "tank refill",
                                Optional.empty(),
                                new Money(6000, CurrencyCode.of("EUR")))),
                MessageReference.newReference());
    }

    private static ProposalReport nothingIdentifiedReport() {
        return new ProposalReport(
                CONVERSATION_ID,
                INBOUND_MESSAGE_ID,
                ReportOutcome.NOTHING_IDENTIFIED,
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
    @DisplayName("deliver(ProposalReport)")
    class Deliver {

        @Test
        @DisplayName(
                "when called with a RECORDED report over a bot WireMock accepts sendMessage for - then exactly one sendMessage is recorded, addressed and replying as the report says, rendered as ProposalReportRenderer.render() says, with no parse_mode")
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
                    .containsExactly(ProposalReportRenderer.render(report));
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
            ProposalReport report = recordedReportWithTwoSummaries();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            assertThatThrownBy(() -> adapter.deliver(report)).isInstanceOf(MessageDeliveryFailedException.class);
        }

        @Test
        @DisplayName(
                "when called over a bot pointed at an address that refuses the connection - then throws MessageDeliveryFailedException carrying the client exception as its cause")
        void
                whenBotIsPointedAtRefusingAddress_thenThrowsMessageDeliveryFailedExceptionCarryingClientExceptionAsCause() {
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

        @Test
        @DisplayName(
                "when called with a RECORDED report carrying two summaries and a reference over a bot WireMock accepts sendMessage for - then the recorded sendMessage carries a reply_markup form param holding one row of two buttons whose texts are Confirm and Delete and whose callback_data values are what ProposalCallbackData.render produces for that reference")
        void
                whenCalledWithRecordedReportCarryingReference_thenSendMessageCarriesReplyMarkupWithConfirmAndDeleteButtons() {
            telegramAcceptsSendMessage(DELIVERY_TOKEN);
            ProposalReport report = recordedReportWithTwoSummaries();
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
        @DisplayName(
                "when called with a NOTHING_IDENTIFIED report with no summaries - then the recorded sendMessage carries no reply_markup form param")
        void whenCalledWithNothingIdentifiedReport_thenSendMessageCarriesNoReplyMarkupFormParam() {
            telegramAcceptsSendMessage(DELIVERY_TOKEN);
            ProposalReport report = nothingIdentifiedReport();
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
        @DisplayName(
                "when called with an ACCEPTED acknowledgement over a bot WireMock accepts both answerCallbackQuery and editMessageReplyMarkup for - then exactly one answerCallbackQuery is recorded carrying the interaction id and the acknowledgement's wording, and exactly one editMessageReplyMarkup is recorded carrying the conversation id, the report message id and no reply_markup form param")
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
                "when called with an ACCEPTED acknowledgement over a bot whose stubs record both calls - then the two recorded Bot API calls, in arrival order, are answerCallbackQuery then editMessageReplyMarkup")
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
        @DisplayName(
                "when called over a bot whose answerCallbackQuery is answered with a non-OK envelope and whose editMessageReplyMarkup is accepted - then throws MessageDeliveryFailedException and the editMessageReplyMarkup is still recorded")
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
        @DisplayName(
                "when called over a bot whose answerCallbackQuery is accepted and whose editMessageReplyMarkup is answered with a non-OK envelope - then throws MessageDeliveryFailedException")
        void whenAnswerCallbackQueryAcceptedAndEditMessageReplyMarkupFails_thenThrowsMessageDeliveryFailedException() {
            telegramAcceptsAnswerCallbackQuery(DELIVERY_TOKEN);
            telegramFailsEditMessageReplyMarkup(DELIVERY_TOKEN, 400, "message is not modified");
            ResolutionAcknowledgement ack = acceptedAcknowledgement();
            TelegramMessageDeliveryAdapter adapter = adapterOver(forToken(DELIVERY_TOKEN));

            assertThatThrownBy(() -> adapter.acknowledge(ack)).isInstanceOf(MessageDeliveryFailedException.class);
        }

        @Test
        @DisplayName(
                "when called over a bot answering both calls with a non-OK envelope, each carrying a distinguishable description - then the thrown MessageDeliveryFailedException's message names the answerCallbackQuery failure's description and not the edit's")
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
                "when called with null over a bot WireMock accepts both calls for - then throws InvalidIncomingMessageException and nothing is sent, as deliver(null) already does")
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
                "when called over a bot pointed at an address that refuses the connection - then throws MessageDeliveryFailedException carrying the client exception as its cause")
        void
                whenBotIsPointedAtRefusingAddress_thenThrowsMessageDeliveryFailedExceptionCarryingClientExceptionAsCause() {
            ResolutionAcknowledgement ack = acceptedAcknowledgement();
            TelegramMessageDeliveryAdapter adapter = adapterOver(refusingBot());

            assertThatThrownBy(() -> adapter.acknowledge(ack))
                    .isInstanceOf(MessageDeliveryFailedException.class)
                    .extracting(Throwable::getCause)
                    .isNotNull();
        }
    }
}
