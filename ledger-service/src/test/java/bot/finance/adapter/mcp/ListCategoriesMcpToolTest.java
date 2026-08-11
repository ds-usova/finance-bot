package bot.finance.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.application.dto.ListCategoriesCommand;
import bot.finance.application.port.ListCategoriesPort;
import bot.finance.common.LogCapture;
import bot.finance.common.boot.McpAdapterTest;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.AuthenticatedUserId;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.List;
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
 * POST to {@code /mcp} - never by calling {@link ListCategoriesMcpTool}'s method directly, since request binding
 * and the tool's own error-to-result mapping live in the adapter body itself. Only {@link ListCategoriesPort} is
 * mocked.
 */
@McpAdapterTest
class ListCategoriesMcpToolTest {

    private static final String RECEIVED_CALL_PREFIX = "Received list_categories call:";

    @LocalServerPort
    private int port;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @MockitoBean
    private ListCategoriesPort listCategoriesPort;

    private String token(long userId) {
        return McpTokens.tokenFor(accessTokenMinter, userId);
    }

    private Response postListCategories(String token, String grouping) {
        return postMcp(token, McpRequests.listCategories(grouping));
    }

    /** Stubs the port to answer {@code categories}, then calls list_categories as {@code userId}. */
    private Response listCategoriesAnswering(long userId, String grouping, List<String> categories) {
        when(listCategoriesPort.list(any())).thenReturn(categories);
        return postListCategories(token(userId), grouping);
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
        @DisplayName("when list_categories is called - then the port receives the token's subject and the "
                + "grouping name")
        void whenListCategoriesIsCalled_thenPortReceivesTokenSubjectAndGroupingName() {
            long userId = 42L;

            listCategoriesAnswering(userId, "Groceries", List.of("Supermarkets", "Markets", "Household Supplies"));

            ArgumentCaptor<ListCategoriesCommand> command = ArgumentCaptor.forClass(ListCategoriesCommand.class);
            verify(listCategoriesPort).list(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(userId));
            assertThat(command.getValue().groupingName()).isEqualTo("Groceries");
        }

        @Test
        @DisplayName("when the port answers a grouping's categories - then the result carries the grouping and "
                + "every category")
        void whenPortAnswersCategories_thenResultCarriesGroupingAndEveryCategory() {
            Response response =
                    listCategoriesAnswering(42L, "Groceries", List.of("Supermarkets", "Markets", "Household Supplies"));

            assertThat(response.jsonPath().getBoolean("result.isError")).isNotEqualTo(true);
            String text = response.jsonPath().getString("result.content[0].text");
            assertThat(text)
                    .contains("\"grouping\":\"Groceries\"")
                    .contains("Supermarkets")
                    .contains("Markets")
                    .contains("Household Supplies");
        }

        @Test
        @DisplayName(
                "when the port answers an empty list - then the result is a non-error carrying an empty categories array")
        void whenPortAnswersEmptyList_thenResultIsNonErrorCarryingEmptyCategoriesArray() {
            Response response = listCategoriesAnswering(43L, "Miscellaneous", List.of());

            assertThat(response.jsonPath().getBoolean("result.isError")).isNotEqualTo(true);
            String text = response.jsonPath().getString("result.content[0].text");
            assertThat(text).contains("\"grouping\":\"Miscellaneous\"").contains("\"categories\":[]");
        }

        @Test
        @DisplayName("when the caller token carries no mrf claim - then the categories are still answered")
        void whenTokenCarriesNoMrfClaim_thenCategoriesAreStillAnswered() {
            String token = McpTokens.noReferenceToken(44L);
            when(listCategoriesPort.list(any())).thenReturn(List.of("Supermarkets"));

            Response response = postListCategories(token, "Groceries");

            assertThat(response.jsonPath().getBoolean("result.isError")).isNotEqualTo(true);
            String text = response.jsonPath().getString("result.content[0].text");
            assertThat(text).contains("Supermarkets");
        }
    }

    @Nested
    @DisplayName("Error Mapping")
    class ErrorMapping {

        @Test
        @DisplayName(
                "when the port throws InvalidCategoryException - then the tool error carries that exception's message")
        void whenPortThrowsInvalidCategoryException_thenToolErrorCarriesThatExceptionsMessage() {
            String exceptionMessage = "no grouping named Fictional is stored for this user";
            when(listCategoriesPort.list(any())).thenThrow(new InvalidCategoryException(exceptionMessage));

            Response response = postListCategories(token(1L), "Fictional");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).contains(exceptionMessage);
        }

        @Test
        @DisplayName(
                "when the port throws InvalidGroupingException - then the tool error carries that exception's message")
        void whenPortThrowsInvalidGroupingException_thenToolErrorCarriesThatExceptionsMessage() {
            String exceptionMessage = "no grouping named Fictional is stored for this user";
            when(listCategoriesPort.list(any())).thenThrow(new InvalidGroupingException(exceptionMessage));

            Response response = postListCategories(token(9L), "Fictional");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).contains(exceptionMessage);
        }

        @Test
        @DisplayName("when the port throws InvalidUserException - then the tool error names an invalid request")
        void whenPortThrowsInvalidUserException_thenToolErrorNamesInvalidRequest() {
            when(listCategoriesPort.list(any())).thenThrow(new InvalidUserException("authenticated user id is blank"));

            Response response = postListCategories(token(2L), "Groceries");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).containsIgnoringCase("invalid");
        }

        @Test
        @DisplayName("when the port throws EntityNotFoundException - then the tool error says the user is unknown, "
                + "naming no external id")
        void whenPortThrowsEntityNotFoundException_thenToolErrorSaysUserIsUnknownWithoutExternalId() {
            when(listCategoriesPort.list(any()))
                    .thenThrow(new EntityNotFoundException("User", "no user stored for external id user-000123"));

            Response response = postListCategories(token(5L), "Groceries");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            String message = response.jsonPath().getString("result.content[0].text");
            assertThat(message).containsIgnoringCase("user").containsIgnoringCase("unknown");
            assertThat(message).doesNotContain("user-000123");
        }

        @Test
        @DisplayName("when the port throws PersistenceFailedException - then the tool error says the categories "
                + "could not be read")
        void whenPortThrowsPersistenceFailedException_thenToolErrorSaysNotReadNamingNoInternals() {
            when(listCategoriesPort.list(any()))
                    .thenThrow(new PersistenceFailedException(
                            "duplicate key value violates unique constraint \"pk_category\" on table \"category\"",
                            new RuntimeException("cause")));

            Response response = postListCategories(token(6L), "Groceries");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            String message = response.jsonPath().getString("result.content[0].text");
            assertThat(message)
                    .containsIgnoringCase("read")
                    .doesNotContain("category\"")
                    .doesNotContainIgnoringCase("constraint");
        }

        @Test
        @DisplayName("when the port throws a RuntimeException outside the failure table - then a generic tool "
                + "error is returned")
        void whenPortThrowsUnrecognizedRuntimeException_thenGenericToolErrorReturned() {
            String secretMessage = "connection pool exhausted on host db-primary-7";
            when(listCategoriesPort.list(any())).thenThrow(new RuntimeException(secretMessage));

            Response response = postListCategories(token(7L), "Groceries");

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text")).doesNotContain(secretMessage);
        }

        @Test
        @DisplayName("when the port throws any failure - then the logged failure names its kind and leaks no "
                + "grouping or token")
        void whenPortThrowsAnyFailure_thenLoggedFailureNamesItsKindAndLeaksNoGroupingOrToken() {
            String secretGrouping = "SecretGrouping123";
            long secretUserId = 999123123L;
            String secretExternalId = Long.toString(secretUserId);
            when(listCategoriesPort.list(any())).thenThrow(new InvalidCategoryException("category is unknown"));
            String issuedToken = token(secretUserId);

            try (LogCapture logCapture = LogCapture.attachedTo(ListCategoriesMcpTool.class)) {
                postListCategories(issuedToken, secretGrouping);

                assertThat(logCapture.messages())
                        .anyMatch(message -> message.contains(InvalidCategoryException.class.getSimpleName()));
                // The received-call line is the DEBUG trace of the request itself, so it carries the grouping name
                // by design; every other line must not.
                assertThat(logCapture.messages())
                        .filteredOn(message -> !message.startsWith(RECEIVED_CALL_PREFIX))
                        .noneMatch(message -> message.contains(secretGrouping)
                                || message.contains(secretExternalId)
                                || message.contains(issuedToken));
                assertThat(logCapture.messages()).noneMatch(message -> message.contains(issuedToken));
            }
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.mcp.ListCategoriesMcpToolTest#invalidGroupings")
        @DisplayName("when grouping is invalid - then the tool error is returned and the port is never called")
        void whenGroupingIsInvalid_thenToolErrorReturnedAndPortNeverCalled(String description, String grouping) {
            Response response = postListCategories(token(8L), grouping);

            assertThat(response.jsonPath().getBoolean("result.isError")).isTrue();
            verify(listCategoriesPort, never()).list(any());
        }
    }

    static Stream<Arguments> invalidGroupings() {
        return Stream.of(arguments("absent", null), arguments("blank", ""));
    }
}
