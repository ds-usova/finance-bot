package bot.finance.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.common.LogCapture;
import bot.finance.common.McpAdapterTest;
import bot.finance.common.McpRequests;
import bot.finance.common.McpTokens;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
            String parentCategory,
            String description,
            String merchant,
            Long amountMinorUnits,
            String currencyCode) {
        return postMcp(
                token,
                McpRequests.createExpenseProposal(
                        category, parentCategory, description, merchant, amountMinorUnits, currencyCode));
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
        @DisplayName(
                "when create_expense_proposal is called - then the port receives a command carrying the token's subject as its identity and the result carries the stored proposal")
        void whenCreateExpenseProposalIsCalled_thenPortReceivesTokenSubjectAndResultCarriesStoredProposal() {
            String externalId = "user-42";
            ExpenseProposal stored = ExpenseProposal.stored(
                    4242L,
                    99L,
                    3L,
                    "lunch with the team",
                    Optional.of("Trattoria Roma"),
                    new Money(1599L, CurrencyCode.of("EUR")),
                    MessageReference.newReference(),
                    CREATED_AT,
                    CREATED_AT);
            when(createExpenseProposalPort.create(any())).thenReturn(stored);

            Response response = postCreateExpenseProposal(
                    token(externalId), "Restaurants", null, "lunch with the team", "Trattoria Roma", 1599L, "EUR");

            ArgumentCaptor<CreateExpenseProposalCommand> command =
                    ArgumentCaptor.forClass(CreateExpenseProposalCommand.class);
            verify(createExpenseProposalPort).create(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(externalId));

            String body = response.getBody().asString();
            assertThat(body)
                    .contains("4242")
                    .contains("Restaurants")
                    .contains("lunch with the team")
                    .contains("Trattoria Roma")
                    .contains("1599")
                    .contains("EUR")
                    .contains("2026-07-30T12:00:00Z");
        }

        @Test
        @DisplayName(
                "when create_expense_proposal is called - then the port receives a command carrying the token's mrf claim as its message reference and the token's subject as its identity")
        void whenCreateExpenseProposalIsCalled_thenPortReceivesTokenMrfClaimAsMessageReferenceAndSubjectAsIdentity() {
            String externalId = "user-43";
            MessageReference reference = MessageReference.newReference();
            String token = McpTokens.tokenFor(accessTokenMinter, externalId, reference);
            ExpenseProposal stored = ExpenseProposal.stored(
                    4343L,
                    99L,
                    3L,
                    "lunch with the team",
                    Optional.of("Trattoria Roma"),
                    new Money(1599L, CurrencyCode.of("EUR")),
                    reference,
                    CREATED_AT,
                    CREATED_AT);
            when(createExpenseProposalPort.create(any())).thenReturn(stored);

            postCreateExpenseProposal(
                    token, "Restaurants", null, "lunch with the team", "Trattoria Roma", 1599L, "EUR");

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
                "when the port throws InvalidExpenseProposalException - then the tool error names the field at fault and nothing about the store")
        void whenPortThrowsInvalidExpenseProposalException_thenToolErrorNamesFieldAtFaultAndNothingAboutStore() {
            when(createExpenseProposalPort.create(any()))
                    .thenThrow(new InvalidExpenseProposalException("description must be present"));

            Response response =
                    postCreateExpenseProposal(token("user-1"), "Restaurants", null, "lunch", "Cafe", 500L, "EUR");

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
                    postCreateExpenseProposal(token("user-2"), "Restaurants", null, "lunch", "Cafe", 500L, "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).containsIgnoringCase("invalid");
        }

        @Test
        @DisplayName(
                "when the currency code is unusable so mapping throws InvalidMoneyException - then the tool error names an invalid request and the port is untouched")
        void whenCurrencyCodeUnusable_thenToolErrorNamesInvalidRequestAndPortUntouched() {
            Response response = postCreateExpenseProposal(
                    token("user-3"), "Restaurants", null, "lunch", "Cafe", 500L, "ZZZ");

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

            Response response =
                    postCreateExpenseProposal(token("user-4"), "Utilities", null, "lunch", "Cafe", 500L, "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).contains(exceptionMessage);
        }

        @Test
        @DisplayName("when the port throws EntityNotFoundException - then the tool error says the user is unknown")
        void whenPortThrowsEntityNotFoundException_thenToolErrorSaysUserIsUnknown() {
            when(createExpenseProposalPort.create(any()))
                    .thenThrow(new EntityNotFoundException("User", "no user stored for external id user-000123"));

            Response response =
                    postCreateExpenseProposal(token("user-5"), "Restaurants", null, "lunch", "Cafe", 500L, "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            String message = response.jsonPath().getString("result.content[0].text");
            assertThat(message).containsIgnoringCase("user").containsIgnoringCase("unknown");
            assertThat(message).doesNotContain("user-000123");
        }

        @Test
        @DisplayName(
                "when the port throws PersistenceFailedException - then the tool error says the proposal could not be stored, naming no table, constraint or stack frame")
        void whenPortThrowsPersistenceFailedException_thenToolErrorSaysNotStoredNamingNoInternals() {
            when(createExpenseProposalPort.create(any()))
                    .thenThrow(new PersistenceFailedException(
                            "duplicate key value violates unique constraint \"pk_expense_proposal\" on table "
                                    + "\"expense_proposal\"",
                            new RuntimeException("cause")));

            Response response =
                    postCreateExpenseProposal(token("user-6"), "Restaurants", null, "lunch", "Cafe", 500L, "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            String message = response.jsonPath().getString("result.content[0].text");
            assertThat(message)
                    .containsIgnoringCase("stored")
                    .doesNotContain("expense_proposal")
                    .doesNotContainIgnoringCase("constraint");
        }

        @Test
        @DisplayName(
                "when the port throws a RuntimeException outside the failure table - then a generic tool error is returned rather than an exception reaching the transport")
        void whenPortThrowsUnrecognizedRuntimeException_thenGenericToolErrorReturned() {
            String secretMessage = "connection pool exhausted on host db-primary-7";
            when(createExpenseProposalPort.create(any())).thenThrow(new RuntimeException(secretMessage));

            Response response =
                    postCreateExpenseProposal(token("user-7"), "Restaurants", null, "lunch", "Cafe", 500L, "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).doesNotContain(secretMessage);
        }

        @Test
        @DisplayName(
                "when the port throws any failure - then a WARN line is logged carrying the failure kind and neither the arguments nor the token")
        void whenPortThrowsAnyFailure_thenWarnLineLogsFailureKindWithoutArgumentsOrToken() {
            String secretCategory = "SecretCategory123";
            String secretDescription = "SecretDescription123";
            String secretMerchant = "SecretMerchant123";
            String secretExternalId = "secret-user-123";
            when(createExpenseProposalPort.create(any()))
                    .thenThrow(new InvalidCategoryException("category is unknown"));
            String issuedToken = token(secretExternalId);

            try (LogCapture logCapture = LogCapture.attachedTo(CreateExpenseProposalMcpTool.class)) {
                postCreateExpenseProposal(
                        issuedToken, secretCategory, null, secretDescription, secretMerchant, 500L, "EUR");

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
                assertThat(logCapture.messages())
                        .noneMatch(message -> message.contains(issuedToken));
            }
        }

        @Test
        @DisplayName("when the caller token carries no mrf claim - then the result is a tool error and the port is never called")
        void whenTokenCarriesNoMrfClaim_thenResultIsToolErrorAndPortNeverCalled() {
            String token = McpTokens.noReferenceToken("user-10");

            Response response =
                    postCreateExpenseProposal(token, "Restaurants", null, "lunch", "Cafe", 500L, "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            verify(createExpenseProposalPort, never()).create(any());
        }

        @Test
        @DisplayName(
                "when the caller token's mrf claim is not a UUID - then the result is a tool error and the port is never called")
        void whenTokenMrfClaimIsNotUuid_thenResultIsToolErrorAndPortNeverCalled() {
            String token = McpTokens.malformedReferenceToken("user-11");

            Response response =
                    postCreateExpenseProposal(token, "Restaurants", null, "lunch", "Cafe", 500L, "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            verify(createExpenseProposalPort, never()).create(any());
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName(
                "when amountMinorUnits is absent - then the tool error names an invalid request and the port is never called")
        void whenAmountMinorUnitsAbsent_thenToolErrorNamesInvalidRequestAndPortNeverCalled() {
            Response response =
                    postCreateExpenseProposal(token("user-8"), "Restaurants", null, "lunch", "Cafe", null, "EUR");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).containsIgnoringCase("amount");
            verify(createExpenseProposalPort, never()).create(any());
        }

        @Test
        @DisplayName(
                "when amountMinorUnits cannot be bound at all - then the framework's own binding failure is reported and the port is never called")
        void whenAmountMinorUnitsCannotBeBound_thenFrameworksOwnBindingFailureReportedAndPortNeverCalled() {
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
                          "parentCategory": null,
                          "description": "lunch",
                          "merchant": "Cafe",
                          "amountMinorUnits": "twelve",
                          "currencyCode": "EUR"
                        }
                      }
                    }
                    """;

            Response response = postMcp(token("user-9"), body);

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            verify(createExpenseProposalPort, never()).create(any());
        }
    }
}
