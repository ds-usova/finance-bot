package bot.finance.ai.adapter.ledger;

import bot.finance.ai.adapter.grpc.CallerTokenTestSupport;
import bot.finance.ai.application.dto.ProposedExpense;
import bot.finance.ai.common.LedgerAdapterTest;
import bot.finance.ai.common.McpLedgerStubs;
import bot.finance.ai.common.WireMockSupport;
import bot.finance.ai.domain.exception.ExpenseProposalFailedException;
import bot.finance.ai.domain.value.Money;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@LedgerAdapterTest
class McpExpenseProposalAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private McpExpenseProposalAdapter adapter;

    @AfterEach
    void tearDown() {
        WireMockSupport.SERVER.resetAll();
    }

    private static List<LoggedRequest> capturedRequests() {
        return WireMockSupport.SERVER.findAll(postRequestedFor(urlPathEqualTo(McpLedgerStubs.MCP_PATH)));
    }

    private static JsonNode requestBody(LoggedRequest request) throws JsonProcessingException {
        return MAPPER.readTree(request.getBodyAsString());
    }

    /**
     * Among the requests a session sent, the one invoking {@code create_expense_proposal} — as opposed to any
     * handshake request the session also performs.
     */
    private static LoggedRequest toolCallRequest(List<LoggedRequest> requests) throws JsonProcessingException {
        for (LoggedRequest request : requests) {
            JsonNode body = requestBody(request);
            if ("create_expense_proposal".equals(body.at("/params/name").asText())) {
                return request;
            }
        }
        throw new AssertionError("no create_expense_proposal call among " + requests);
    }

    private static ProposedExpense proposedExpense(Optional<String> parentCategoryName) {
        return new ProposedExpense("Groceries", parentCategoryName, "milk", Money.of("12.34", "EUR"));
    }

    @Nested
    @DisplayName("propose()")
    class Propose {

        @Test
        @DisplayName("when the ledger accepts the proposal and a caller token is held in the context - "
                + "then the tool call carries category, parentCategory, description, amountMinorUnits and "
                + "currencyCode, no merchant, and every request on the session carries the caller's token")
        void whenAcceptedWithParentCategoryAndToken_thenToolCallCarriesArgumentsAndEveryRequestCarriesToken()
                throws JsonProcessingException {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();

            CallerTokenTestSupport.withCallerToken(
                    "Bearer caller-token-1", () -> adapter.propose(proposedExpense(Optional.of("Food"))));

            List<LoggedRequest> requests = capturedRequests();
            assertThat(requests).isNotEmpty();
            for (LoggedRequest request : requests) {
                assertThat(request.getHeader("Authorization")).isEqualTo("Bearer caller-token-1");
            }

            JsonNode arguments = requestBody(toolCallRequest(requests)).at("/params/arguments");
            assertThat(arguments.get("category").asText()).isEqualTo("Groceries");
            assertThat(arguments.get("parentCategory").asText()).isEqualTo("Food");
            assertThat(arguments.get("description").asText()).isEqualTo("milk");
            assertThat(arguments.get("amountMinorUnits").asLong()).isEqualTo(1234L);
            assertThat(arguments.get("currencyCode").asText()).isEqualTo("EUR");
            assertThat(arguments.has("merchant")).isFalse();
        }

        @Test
        @DisplayName("when the parent category name is empty - then the tool call carries no parentCategory")
        void whenParentCategoryNameEmpty_thenToolCallCarriesNoParentCategory() throws JsonProcessingException {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();

            CallerTokenTestSupport.withCallerToken(
                    "Bearer caller-token-1", () -> adapter.propose(proposedExpense(Optional.empty())));

            JsonNode arguments = requestBody(toolCallRequest(capturedRequests())).at("/params/arguments");
            assertThat(arguments.has("parentCategory")).isFalse();
        }

        @Test
        @DisplayName("when the ledger answers a tool result flagged isError - "
                + "then ExpenseProposalFailedException is thrown, reporting a refusal")
        void whenLedgerRefuses_thenThrowsExpenseProposalFailedExceptionReportingRefusal() {
            McpLedgerStubs.stubCreateExpenseProposalRefused();

            assertThatThrownBy(() -> CallerTokenTestSupport.withCallerToken(
                            "Bearer caller-token-1", () -> adapter.propose(proposedExpense(Optional.empty()))))
                    .isInstanceOf(ExpenseProposalFailedException.class)
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                            ExpenseProposalFailedException.class))
                    .extracting(ExpenseProposalFailedException::reason)
                    .isEqualTo(ExpenseProposalFailedException.Reason.REFUSED);
        }

        @Test
        @DisplayName("when the ledger fails the transport - "
                + "then ExpenseProposalFailedException is thrown, reporting an unreachable ledger")
        void whenTransportFails_thenThrowsExpenseProposalFailedExceptionReportingUnreachable() {
            McpLedgerStubs.stubCreateExpenseProposalTransportFailure();

            assertThatThrownBy(() -> CallerTokenTestSupport.withCallerToken(
                            "Bearer caller-token-1", () -> adapter.propose(proposedExpense(Optional.empty()))))
                    .isInstanceOf(ExpenseProposalFailedException.class)
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                            ExpenseProposalFailedException.class))
                    .extracting(ExpenseProposalFailedException::reason)
                    .isEqualTo(ExpenseProposalFailedException.Reason.UNREACHABLE);
        }

        @Test
        @DisplayName("when no caller token is held in the context - "
                + "then ExpenseProposalFailedException is thrown, reporting an unreachable ledger, "
                + "and the ledger is never called")
        void whenNoCallerToken_thenThrowsExpenseProposalFailedExceptionReportingUnreachableAndLedgerNeverCalled() {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();

            assertThatThrownBy(() -> adapter.propose(proposedExpense(Optional.empty())))
                    .isInstanceOf(ExpenseProposalFailedException.class)
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                            ExpenseProposalFailedException.class))
                    .extracting(ExpenseProposalFailedException::reason)
                    .isEqualTo(ExpenseProposalFailedException.Reason.UNREACHABLE);

            assertThat(capturedRequests()).isEmpty();
        }

        @Test
        @DisplayName("when propose() is called twice - "
                + "then each call performs its own MCP session and each carries its own token")
        void whenCalledTwice_thenEachCallCarriesItsOwnToken() throws JsonProcessingException {
            McpLedgerStubs.stubCreateExpenseProposalAccepted();

            CallerTokenTestSupport.withCallerToken(
                    "Bearer caller-token-1", () -> adapter.propose(proposedExpense(Optional.empty())));
            CallerTokenTestSupport.withCallerToken(
                    "Bearer caller-token-2", () -> adapter.propose(proposedExpense(Optional.empty())));

            List<LoggedRequest> requests = capturedRequests();
            List<LoggedRequest> toolCalls = requests.stream()
                    .filter(request -> {
                        try {
                            return "create_expense_proposal".equals(
                                    requestBody(request).at("/params/name").asText());
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
            assertThat(toolCalls).hasSize(2);
            assertThat(toolCalls.get(0).getHeader("Authorization")).isEqualTo("Bearer caller-token-1");
            assertThat(toolCalls.get(1).getHeader("Authorization")).isEqualTo("Bearer caller-token-2");
        }
    }
}
