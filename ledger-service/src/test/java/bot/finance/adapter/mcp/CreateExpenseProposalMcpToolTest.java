package bot.finance.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.common.LogCapture;
import bot.finance.common.boot.McpAdapterTest;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test for the inbound MCP-tool adapter. Enters through the protocol - a JSON-RPC {@code tools/call}
 * POST to {@code /mcp} - never by calling {@link CreateExpenseProposalMcpTool}'s method directly, since request
 * binding and the tool's own error-to-result mapping live in the adapter body itself. Only
 * {@link CreateExpenseProposalPort} is mocked.
 */
@McpAdapterTest
class CreateExpenseProposalMcpToolTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-30T12:00:00Z");

    private static final String RECEIVED_CALL_PREFIX = "Received create_expense_proposal call:";

    @LocalServerPort
    private int port;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @MockitoBean
    private CreateExpenseProposalPort createExpenseProposalPort;

    private String token(String externalId) {
        return McpTokens.tokenFor(accessTokenMinter, externalId);
    }

    private Response postCreateExpenseProposal(
            String token,
            String category,
            String grouping,
            String description,
            String merchant,
            String amount,
            String currencyCode) {
        return postMcp(
                token,
                McpRequests.createExpenseProposal(category, grouping, description, merchant, amount, currencyCode));
    }

    /**
     * Stubs the port to answer a proposal stored under {@code reference}, then calls create_expense_proposal for
     * Restaurants-under-Dining - what the happy-path scenarios arrange from.
     */
    private Response postAcceptedProposal(String token, MessageReference reference) {
        ExpenseProposal stored = ExpenseProposal.stored(
                4242L,
                99L,
                3L,
                "lunch with the team",
                Optional.of("Trattoria Roma"),
                new Money(1599L, CurrencyCode.of("EUR")),
                reference,
                CREATED_AT,
                CREATED_AT);
        when(createExpenseProposalPort.create(any())).thenReturn(stored);

        return postCreateExpenseProposal(
                token, "Restaurants", "Dining", "lunch with the team", "Trattoria Roma", "15.99", "EUR");
    }

    private Response postMcp(String token, String body) {
        return RestAssured.given()
                .port(port)
                .contentType(ContentType.JSON)
                .accept(McpRequests.ACCEPT_HEADER)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .when()
                .post("/mcp")
                .then()
                .extract()
                .response();
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("when create_expense_proposal is called - then the port receives the token's subject and the "
                + "grouping name")
        void whenCreateExpenseProposalIsCalled_thenPortReceivesTokenSubjectAndGroupingName() {
            String externalId = "user-42";

            postAcceptedProposal(token(externalId), MessageReference.newReference());

            ArgumentCaptor<CreateExpenseProposalCommand> command =
                    ArgumentCaptor.forClass(CreateExpenseProposalCommand.class);
            verify(createExpenseProposalPort).create(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(externalId));
            assertThat(command.getValue().groupingName()).isEqualTo("Dining");
        }

        @Test
        @DisplayName("when create_expense_proposal is called - then the result carries the stored proposal")
        void whenCreateExpenseProposalIsCalled_thenResultCarriesStoredProposal() {
            Response response = postAcceptedProposal(token("user-42"), MessageReference.newReference());

            String body = response.getBody().asString();
            assertThat(body)
                    .contains("4242")
                    .contains("Restaurants")
                    .contains("lunch with the team")
                    .contains("Trattoria Roma")
                    .contains("15.99")
                    .contains("EUR")
                    .contains("2026-07-30T12:00:00Z");
        }

        @Test
        @DisplayName("when the caller token carries an mrf claim - then the port receives it as the command's "
                + "message reference")
        void whenTokenCarriesMrfClaim_thenPortReceivesItAsTheCommandsMessageReference() {
            String externalId = "user-43";
            MessageReference reference = MessageReference.newReference();

            postAcceptedProposal(McpTokens.tokenFor(accessTokenMinter, externalId, reference), reference);

            ArgumentCaptor<CreateExpenseProposalCommand> command =
                    ArgumentCaptor.forClass(CreateExpenseProposalCommand.class);
            verify(createExpenseProposalPort).create(command.capture());
            assertThat(command.getValue().messageReference()).isEqualTo(reference);
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(externalId));
        }
    }

    @Nested
    @DisplayName("Error Mapping")
    class ErrorMapping {

        @Test
        @DisplayName(
                "when the port throws InvalidExpenseProposalException - then the tool error names the field at fault")
        void whenPortThrowsInvalidExpenseProposalException_thenToolErrorNamesFieldAtFault() {
            when(createExpenseProposalPort.create(any()))
                    .thenThrow(new InvalidExpenseProposalException("description must be present"));

            Response response =
                    postCreateExpenseProposal(token("user-1"), "Restaurants", "Dining", "lunch", "Cafe", "5.00", "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            String message = response.jsonPath().getString("result.content[0].text");
            assertThat(message)
                    .contains("description must be present")
                    .doesNotContainIgnoringCase("table")
                    .doesNotContainIgnoringCase("constraint");
        }

        @Test
        @DisplayName("when the port throws InvalidUserException - then the tool error names an invalid request")
        void whenPortThrowsInvalidUserException_thenToolErrorNamesInvalidRequest() {
            when(createExpenseProposalPort.create(any()))
                    .thenThrow(new InvalidUserException("authenticated user id is blank"));

            Response response =
                    postCreateExpenseProposal(token("user-2"), "Restaurants", "Dining", "lunch", "Cafe", "5.00", "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).containsIgnoringCase("invalid");
        }

        @Test
        @DisplayName("when the currency code is unusable - then the tool error names an invalid request and the "
                + "port is untouched")
        void whenCurrencyCodeUnusable_thenToolErrorNamesInvalidRequestAndPortUntouched() {
            Response response =
                    postCreateExpenseProposal(token("user-3"), "Restaurants", "Dining", "lunch", "Cafe", "5.00", "ZZZ");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).contains("ZZZ");
            verify(createExpenseProposalPort, never()).create(any());
        }

        @Test
        @DisplayName(
                "when the port throws InvalidCategoryException - then the tool error carries that exception's message")
        void whenPortThrowsInvalidCategoryException_thenToolErrorCarriesThatExceptionsMessage() {
            String exceptionMessage = "category 'Utilities' is a grouping - choose one of [Electricity, Water]";
            when(createExpenseProposalPort.create(any())).thenThrow(new InvalidCategoryException(exceptionMessage));

            Response response = postCreateExpenseProposal(
                    token("user-4"), "Utilities", "Utilities", "lunch", "Cafe", "5.00", "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).contains(exceptionMessage);
        }

        @Test
        @DisplayName(
                "when the port throws InvalidGroupingException - then the tool error carries that exception's message bare")
        void whenPortThrowsInvalidGroupingException_thenToolErrorCarriesThatExceptionsMessage() {
            String exceptionMessage = "no grouping named Utilities is stored for this user";
            when(createExpenseProposalPort.create(any())).thenThrow(new InvalidGroupingException(exceptionMessage));

            Response response = postCreateExpenseProposal(
                    token("user-45"), "Electricity", "Utilities", "lunch", "Cafe", "5.00", "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).contains(exceptionMessage);
        }

        @Test
        @DisplayName("when the port throws EntityNotFoundException - then the tool error says the user is unknown")
        void whenPortThrowsEntityNotFoundException_thenToolErrorSaysUserIsUnknown() {
            when(createExpenseProposalPort.create(any()))
                    .thenThrow(new EntityNotFoundException("User", "no user stored for external id user-000123"));

            Response response =
                    postCreateExpenseProposal(token("user-5"), "Restaurants", "Dining", "lunch", "Cafe", "5.00", "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            String message = response.jsonPath().getString("result.content[0].text");
            assertThat(message).containsIgnoringCase("user").containsIgnoringCase("unknown");
            assertThat(message).doesNotContain("user-000123");
        }

        @Test
        @DisplayName("when the port throws PersistenceFailedException - then the tool error says the proposal "
                + "could not be stored")
        void whenPortThrowsPersistenceFailedException_thenToolErrorSaysNotStoredNamingNoInternals() {
            when(createExpenseProposalPort.create(any()))
                    .thenThrow(new PersistenceFailedException(
                            "duplicate key value violates unique constraint \"pk_expense_proposal\" on table "
                                    + "\"expense_proposal\"",
                            new RuntimeException("cause")));

            Response response =
                    postCreateExpenseProposal(token("user-6"), "Restaurants", "Dining", "lunch", "Cafe", "5.00", "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            String message = response.jsonPath().getString("result.content[0].text");
            assertThat(message)
                    .containsIgnoringCase("stored")
                    .doesNotContain("expense_proposal")
                    .doesNotContainIgnoringCase("constraint");
        }

        @Test
        @DisplayName("when the port throws a RuntimeException outside the failure table - then a generic tool "
                + "error is returned")
        void whenPortThrowsUnrecognizedRuntimeException_thenGenericToolErrorReturned() {
            String secretMessage = "connection pool exhausted on host db-primary-7";
            when(createExpenseProposalPort.create(any())).thenThrow(new RuntimeException(secretMessage));

            Response response =
                    postCreateExpenseProposal(token("user-7"), "Restaurants", "Dining", "lunch", "Cafe", "5.00", "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).doesNotContain(secretMessage);
        }

        @Test
        @DisplayName("when the port throws any failure - then the logged failure names its kind and leaks no "
                + "argument or token")
        void whenPortThrowsAnyFailure_thenLoggedFailureNamesItsKindAndLeaksNoArgumentOrToken() {
            String secretCategory = "SecretCategory123";
            String secretDescription = "SecretDescription123";
            String secretMerchant = "SecretMerchant123";
            String secretExternalId = "secret-user-123";
            when(createExpenseProposalPort.create(any()))
                    .thenThrow(new InvalidCategoryException("category is unknown"));
            String issuedToken = token(secretExternalId);

            try (LogCapture logCapture = LogCapture.attachedTo(CreateExpenseProposalMcpTool.class)) {
                postCreateExpenseProposal(
                        issuedToken,
                        secretCategory,
                        "SecretGrouping123",
                        secretDescription,
                        secretMerchant,
                        "5.00",
                        "EUR");

                assertThat(logCapture.messages())
                        .anyMatch(message -> message.contains(InvalidCategoryException.class.getSimpleName()));
                // The received-call line is the DEBUG trace of the request itself, so it carries the arguments
                // by design; every other line must not.
                assertThat(logCapture.messages())
                        .filteredOn(message -> !message.startsWith(RECEIVED_CALL_PREFIX))
                        .noneMatch(message -> message.contains(secretCategory)
                                || message.contains(secretDescription)
                                || message.contains(secretMerchant)
                                || message.contains(secretExternalId)
                                || message.contains(issuedToken));
                assertThat(logCapture.messages()).noneMatch(message -> message.contains(issuedToken));
            }
        }

        @Test
        @DisplayName(
                "when the caller token carries no mrf claim - then the result is a tool error and the port is never called")
        void whenTokenCarriesNoMrfClaim_thenResultIsToolErrorAndPortNeverCalled() {
            String token = McpTokens.noReferenceToken("user-10");

            Response response =
                    postCreateExpenseProposal(token, "Restaurants", "Dining", "lunch", "Cafe", "5.00", "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            verify(createExpenseProposalPort, never()).create(any());
        }

        @Test
        @DisplayName(
                "when the caller token's mrf claim is not a UUID - then the result is a tool error and the port is never called")
        void whenTokenMrfClaimIsNotUuid_thenResultIsToolErrorAndPortNeverCalled() {
            String token = McpTokens.malformedReferenceToken("user-11");

            Response response =
                    postCreateExpenseProposal(token, "Restaurants", "Dining", "lunch", "Cafe", "5.00", "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            verify(createExpenseProposalPort, never()).create(any());
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName(
                "when amount is absent - then the tool error names an invalid request and the port is never called")
        void whenAmountAbsent_thenToolErrorNamesInvalidRequestAndPortNeverCalled() {
            Response response =
                    postCreateExpenseProposal(token("user-8"), "Restaurants", "Dining", "lunch", "Cafe", null, "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).containsIgnoringCase("amount");
            verify(createExpenseProposalPort, never()).create(any());
        }

        @Test
        @DisplayName("when amount cannot be bound to its String type at all - then the call is refused and the "
                + "port is never called")
        void whenAmountCannotBeBoundToString_thenCallIsRefusedAndPortNeverCalled() {
            String body =
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "method": "tools/call",
                      "params": {
                        "name": "create_expense_proposal",
                        "arguments": {
                          "category": "Restaurants",
                          "grouping": "Dining",
                          "description": "lunch",
                          "merchant": "Cafe",
                          "amount": { "value": 7200 },
                          "currencyCode": "EUR"
                        }
                      }
                    }
                    """;

            Response response = postMcp(token("user-9"), body);

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            verify(createExpenseProposalPort, never()).create(any());
        }

        @Test
        @DisplayName(
                "when the amount is sent as a JSON number - then the call is refused and the port is never called")
        void whenAmountIsSentAsJsonNumber_thenCallIsRefusedAndPortNeverCalled() {
            String body =
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "method": "tools/call",
                      "params": {
                        "name": "create_expense_proposal",
                        "arguments": {
                          "category": "Restaurants",
                          "grouping": "Dining",
                          "description": "lunch with the team",
                          "merchant": "Trattoria Roma",
                          "amount": 7200,
                          "currencyCode": "HUF"
                        }
                      }
                    }
                    """;

            Response response = postMcp(token("user-44"), body);

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            verify(createExpenseProposalPort, never()).create(any());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidAmounts")
        @DisplayName(
                "when the amount is invalid - then the tool error names an invalid request and the port is never called")
        void whenAmountIsInvalid_thenToolErrorNamesInvalidRequestAndPortNeverCalled(
                String description, String amount, String currencyCode, String expectedMessageFragment) {
            Response response = postCreateExpenseProposal(
                    token("user-12"), "Restaurants", "Dining", "lunch", "Cafe", amount, currencyCode);

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            String message = response.jsonPath().getString("result.content[0].text");
            assertThat(message).containsIgnoringCase("invalid request").contains(expectedMessageFragment);
            verify(createExpenseProposalPort, never()).create(any());
        }

        static Stream<Arguments> invalidAmounts() {
            return Stream.of(
                    arguments("malformed text form", "7,200", "EUR", "amount must be digits with an optional dot"),
                    arguments(
                            "more precise than the currency",
                            "12.505",
                            "EUR",
                            "12.505 is more precise than EUR, which has 2 decimal places"),
                    arguments(
                            "currency with no minor unit",
                            "1.00",
                            "XAU",
                            "XAU is not a currency an amount can be recorded in"),
                    arguments(
                            "too large for long minor units",
                            "999999999999999999",
                            "EUR",
                            "Amount is too large to record"));
        }

        @Test
        @DisplayName("when grouping is absent from the call - then the rejection names the missing grouping and "
                + "the port is never called")
        void whenGroupingAbsent_thenRejectionNamesMissingGroupingAndPortNeverCalled() {
            String body =
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "method": "tools/call",
                      "params": {
                        "name": "create_expense_proposal",
                        "arguments": {
                          "category": "Restaurants",
                          "description": "lunch",
                          "merchant": "Cafe",
                          "amount": "5.00",
                          "currencyCode": "EUR"
                        }
                      }
                    }
                    """;

            Response response = postMcp(token("user-13"), body);

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).containsIgnoringCase("grouping");
            verify(createExpenseProposalPort, never()).create(any());
        }

        @Test
        @DisplayName(
                "when grouping is blank - then the tool error names an invalid request with no grouping and the port is never called")
        void whenGroupingBlank_thenToolErrorNamesInvalidRequestWithNoGroupingAndPortNeverCalled() {
            Response response =
                    postCreateExpenseProposal(token("user-14"), "Restaurants", "   ", "lunch", "Cafe", "5.00", "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text"))
                    .containsIgnoringCase("invalid request")
                    .contains("expense proposal request has no grouping");
            verify(createExpenseProposalPort, never()).create(any());
        }
    }
}
