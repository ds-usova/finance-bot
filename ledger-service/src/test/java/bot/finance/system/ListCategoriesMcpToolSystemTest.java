package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.application.port.UserRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Grouping;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Drives {@code POST /mcp} - the {@code tools/call list_categories} JSON-RPC method - end to end against the fully
 * wired application. The user and their category tree are seeded through the wired {@link UserRepository} with
 * {@link Grouping#defaults()}, the tree's only writer, never through a {@code bot.finance.common} row helper.
 */
class ListCategoriesMcpToolSystemTest extends AbstractSystemTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    private User seedUserWithDefaultCategories(String externalId) {
        return userRepository.create(User.newUser(externalId), Grouping.defaults());
    }

    private Response callListCategories(String token, String requestBody) {
        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(McpRequests.ACCEPT_HEADER)
                .header("Authorization", "Bearer " + token)
                .body(requestBody)
                .when()
                .post("/mcp");
        logResponse(response);
        return response;
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @Disabled("GI07: UserRepositoryAdapter.findById answers Optional.empty(), so the authenticated caller is "
                + "always refused as unknown")
        @DisplayName("when tools/call list_categories is posted naming a grouping - then its seeded categories "
                + "are listed, sorted by name")
        void whenToolCallNamesGrouping_thenSeededCategoriesAreListedSortedByName() {
            User user = seedUserWithDefaultCategories("list-categories-happy-path-user");
            String token = McpTokens.tokenFor(accessTokenMinter, user.id().orElseThrow());

            String requestBody = McpRequests.listCategories("Groceries");

            Response response = callListCategories(token, requestBody);

            assertThat(response.statusCode()).as("HTTP status").isEqualTo(200);
            assertThat(response.jsonPath().getBoolean("result.isError"))
                    .as("tool result isError")
                    .isNotEqualTo(Boolean.TRUE);

            String toolResultText = response.jsonPath().getString("result.content[0].text");
            assertThat(toolResultText).as("tool result text").isNotNull();
            JsonPath toolResult = new JsonPath(toolResultText);
            assertThat(toolResult.getString("grouping")).as("returned grouping").isEqualTo("Groceries");
            assertThat(toolResult.getList("categories", String.class))
                    .as("returned categories, sorted by name")
                    .containsExactly("Household Supplies", "Markets", "Supermarkets");
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @Disabled("GI07: UserRepositoryAdapter.findById answers Optional.empty(), so the tool answers \"the user is "
                + "unknown\" before the category/grouping distinction is ever checked")
        @DisplayName("when tools/call list_categories names a category rather than a grouping - then the tool "
                + "error says so")
        void whenToolCallNamesCategory_thenToolErrorSaysItIsACategoryNotAGrouping() {
            User user = seedUserWithDefaultCategories("list-categories-unhappy-path-user");
            String token = McpTokens.tokenFor(accessTokenMinter, user.id().orElseThrow());

            String requestBody = McpRequests.listCategories("Supermarkets");

            Response response = callListCategories(token, requestBody);

            assertThat(response.statusCode()).as("HTTP status").isEqualTo(200);
            assertThat(response.jsonPath().getBoolean("result.isError"))
                    .as("tool result isError")
                    .isTrue();

            String toolResultText = response.jsonPath().getString("result.content[0].text");
            assertThat(toolResultText)
                    .as("tool error message says the name is a category, not a grouping")
                    .isEqualTo("Supermarkets is a category, not a grouping");
        }
    }
}
