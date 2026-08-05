package bot.finance.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.application.dto.SummarizeSpendingCommand;
import bot.finance.application.port.SummarizeSpendingPort;
import bot.finance.common.LogCapture;
import bot.finance.common.McpAdapterTest;
import bot.finance.common.McpRequests;
import bot.finance.common.McpTokens;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.exception.InvalidSpendingQueryException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.SpendingPeriod;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.LocalDate;
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
 * POST to {@code /mcp} - never by calling {@link SummarizeSpendingMcpTool}'s method directly, since request
 * binding and the tool's own error-to-result mapping live in the adapter body itself. Only
 * {@link SummarizeSpendingPort} is mocked.
 */
@McpAdapterTest
class SummarizeSpendingMcpToolTest {

    private static final String RECEIVED_CALL_PREFIX = "Received summarize_spending call:";

    @LocalServerPort
    private int port;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @MockitoBean
    private SummarizeSpendingPort summarizeSpendingPort;

    private String token(String externalId) {
        return McpTokens.tokenFor(accessTokenMinter, externalId);
    }

    private String tokenWithReference(String externalId, MessageReference reference) {
        return McpTokens.tokenFor(accessTokenMinter, externalId, reference);
    }

    private Response postSummarizeSpending(String token, String from, String to) {
        return postMcp(token, McpRequests.summarizeSpending(from, to));
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
                "when summarize_spending is called with a first and last day under a valid token carrying an mrf claim - then the port receives the token's identity, message reference and both written days, and the result carries the accepted period with no amount")
        void whenSummarizeSpendingIsCalled_thenPortReceivesIdentityReferenceAndDaysAndResultCarriesAcceptedPeriod() {
            String externalId = "user-101";
            MessageReference reference = MessageReference.newReference();
            String from = "2026-07-27";
            String to = "2026-08-02";
            when(summarizeSpendingPort.summarize(any()))
                    .thenReturn(new SpendingPeriod(LocalDate.parse(from), LocalDate.parse(to)));

            Response response = postSummarizeSpending(tokenWithReference(externalId, reference), from, to);

            ArgumentCaptor<SummarizeSpendingCommand> command = ArgumentCaptor.forClass(SummarizeSpendingCommand.class);
            verify(summarizeSpendingPort).summarize(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(externalId));
            assertThat(command.getValue().reference()).isEqualTo(reference);
            assertThat(command.getValue().from()).isEqualTo(from);
            assertThat(command.getValue().to()).isEqualTo(to);

            assertThat(response.jsonPath().getBoolean("result.isError")).isNotEqualTo(true);
            String text = response.jsonPath().getString("result.content[0].text");
            assertThat(text).contains("\"from\":\"" + from + "\"").contains("\"to\":\"" + to + "\"");
            assertThat(text).doesNotContainIgnoringCase("amount");
        }
    }

    @Nested
    @DisplayName("Error Mapping")
    class ErrorMapping {

        @Test
        @DisplayName(
                "when the port throws InvalidSpendingPeriodException - then the tool error carries that exception's own message")
        void whenPortThrowsInvalidSpendingPeriodException_thenToolErrorCarriesThatExceptionsMessage() {
            String exceptionMessage = "the period ends before it starts";
            when(summarizeSpendingPort.summarize(any())).thenThrow(new InvalidSpendingPeriodException(exceptionMessage));

            Response response = postSummarizeSpending(token("user-1"), "2026-08-05", "2026-08-01");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).contains(exceptionMessage);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.mcp.SummarizeSpendingMcpToolTest#invalidRequestFailures")
        @DisplayName("when the port throws InvalidSpendingQueryException or InvalidUserException - "
                + "then the tool error names an invalid request")
        void whenPortThrowsInvalidRequestFailure_thenToolErrorNamesInvalidRequest(
                String description, RuntimeException failure) {
            when(summarizeSpendingPort.summarize(any())).thenThrow(failure);

            Response response = postSummarizeSpending(token("user-2"), "2026-08-01", "2026-08-05");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).containsIgnoringCase("invalid");
        }

        @Test
        @DisplayName(
                "when the port throws EntityNotFoundException - then the tool error says the user is unknown, carrying neither the external id nor anything else from the exception")
        void whenPortThrowsEntityNotFoundException_thenToolErrorSaysUserIsUnknownWithoutExternalId() {
            when(summarizeSpendingPort.summarize(any()))
                    .thenThrow(new EntityNotFoundException("User", "no user stored for external id user-000456"));

            Response response = postSummarizeSpending(token("user-5"), "2026-08-01", "2026-08-05");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            String message = response.jsonPath().getString("result.content[0].text");
            assertThat(message).containsIgnoringCase("user").containsIgnoringCase("unknown");
            assertThat(message).doesNotContain("user-000456");
        }

        @Test
        @DisplayName(
                "when the port throws PersistenceFailedException - then the tool error says the summary could not be recorded, naming neither the table nor the constraint")
        void whenPortThrowsPersistenceFailedException_thenToolErrorSaysNotRecordedNamingNoInternals() {
            when(summarizeSpendingPort.summarize(any()))
                    .thenThrow(new PersistenceFailedException(
                            "duplicate key value violates unique constraint \"pk_spending_query\" on table \"spending_query\"",
                            new RuntimeException("cause")));

            Response response = postSummarizeSpending(token("user-6"), "2026-08-01", "2026-08-05");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            String message = response.jsonPath().getString("result.content[0].text");
            assertThat(message)
                    .containsIgnoringCase("recorded")
                    .doesNotContain("spending_query\"")
                    .doesNotContainIgnoringCase("constraint");
        }

        @Test
        @DisplayName(
                "when a token carrying no mrf claim is used - then the catch-all tool error is returned and the port is never called")
        void whenTokenCarriesNoMrfClaim_thenCatchAllToolErrorReturnedAndPortNeverCalled() {
            String noMrfToken = McpTokens.noReferenceToken("user-44");

            Response response = postSummarizeSpending(noMrfToken, "2026-08-01", "2026-08-05");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text"))
                    .containsIgnoringCase("could not be summarized");
            verify(summarizeSpendingPort, never()).summarize(any());
        }

        @Test
        @DisplayName(
                "when the port throws a RuntimeException outside the failure table - then the catch-all tool error is returned rather than an exception reaching the transport")
        void whenPortThrowsUnrecognizedRuntimeException_thenCatchAllToolErrorReturnedWithoutSecretMessage() {
            String secretMessage = "connection pool exhausted on host db-primary-9";
            when(summarizeSpendingPort.summarize(any())).thenThrow(new RuntimeException(secretMessage));

            Response response = postSummarizeSpending(token("user-7"), "2026-08-01", "2026-08-05");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).doesNotContain(secretMessage);
        }

        @Test
        @DisplayName(
                "when the port throws any failure - then a WARN line names the failure's class and message, and no line other than the debug received-call trace carries the token")
        void whenPortThrowsAnyFailure_thenWarnLineNamesFailureWithoutLeakingToken() {
            String failureMessage = "spending query is invalid";
            when(summarizeSpendingPort.summarize(any())).thenThrow(new InvalidSpendingQueryException(failureMessage));
            String issuedToken = token("user-secret-77");

            try (LogCapture logCapture = LogCapture.attachedTo(SummarizeSpendingMcpTool.class)) {
                postSummarizeSpending(issuedToken, "2026-08-01", "2026-08-05");

                assertThat(logCapture.messages())
                        .anyMatch(message -> message.contains(InvalidSpendingQueryException.class.getSimpleName())
                                && message.contains(failureMessage));
                assertThat(logCapture.messages())
                        .filteredOn(message -> !message.startsWith(RECEIVED_CALL_PREFIX))
                        .noneMatch(message -> message.contains(issuedToken));
                assertThat(logCapture.messages()).noneMatch(message -> message.contains(issuedToken));
            }
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.mcp.SummarizeSpendingMcpToolTest#invalidDayCases")
        @DisplayName("when from or to is absent or blank - then the written value reaches the port unchanged and "
                + "the tool error carries InvalidSpendingPeriodException's own message")
        void whenFromOrToIsAbsentOrBlank_thenValueReachesPortUnchangedAndToolErrorCarriesExceptionMessage(
                String description, String from, String to) {
            String exceptionMessage = "the period must carry both a first and a last day";
            when(summarizeSpendingPort.summarize(any())).thenThrow(new InvalidSpendingPeriodException(exceptionMessage));

            Response response = postSummarizeSpending(token("user-8"), from, to);

            ArgumentCaptor<SummarizeSpendingCommand> command = ArgumentCaptor.forClass(SummarizeSpendingCommand.class);
            verify(summarizeSpendingPort).summarize(command.capture());
            assertThat(command.getValue().from()).isEqualTo(from);
            assertThat(command.getValue().to()).isEqualTo(to);

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).contains(exceptionMessage);
        }
    }

    static Stream<Arguments> invalidRequestFailures() {
        return Stream.of(
                arguments(
                        "InvalidSpendingQueryException",
                        new InvalidSpendingQueryException("spending query is invalid")),
                arguments("InvalidUserException", new InvalidUserException("authenticated user id is blank")));
    }

    static Stream<Arguments> invalidDayCases() {
        return Stream.of(
                arguments("from absent", null, "2026-08-05"),
                arguments("from blank", "", "2026-08-05"),
                arguments("to absent", "2026-08-01", null),
                arguments("to blank", "2026-08-01", ""));
    }
}
