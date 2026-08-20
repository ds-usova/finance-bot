package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.application.port.UserRepository;
import bot.finance.common.boot.CdcCaptureTest;
import bot.finance.common.fixtures.ChangeStreamEntries;
import bot.finance.common.fixtures.ChangeStreamEntries.ChangeStreamEntry;
import bot.finance.common.fixtures.IncomingMessages;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.OutboxRowUtils;
import bot.finance.domain.model.User;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Grouping;
import bot.finance.domain.value.IncomingMessageId;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Covers {@code POST /mcp} - the {@code tools/call create_expense_proposal} JSON-RPC method - end to end against
 * the fully wired application with capture switched on, entered the way the AI connector reaches it: a signed MCP
 * token carrying the caller's id and the incoming message's reference.
 */
@CdcCaptureTest
class ProposalFactsSystemTest {

    private static final String STREAM_KEY = CdcCaptureTest.STREAM_KEY;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    private Response callCreateExpenseProposal(String token, String requestBody) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(McpRequests.ACCEPT_HEADER)
                .header("Authorization", "Bearer " + token)
                .body(requestBody)
                .when()
                .post("/mcp");
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName(
                "when a proposal is created - then a ProposalCreated entry reaches the stream and the outbox is empty")
        void whenProposalCreatedThroughTool_thenEntryReachesStreamAndOutboxIsEmpty() {
            User user = userRepository.create(User.newUser("proposal-facts-happy-user"), Grouping.defaults());
            long userId = user.id().orElseThrow();
            IncomingMessageId reference = IncomingMessages.newIncomingMessageId();
            String token = McpTokens.tokenFor(accessTokenMinter, userId, reference);

            String requestBody = McpRequests.createExpenseProposal(
                    "Supermarkets", "Groceries", "Milk", "Corner Shop", "7200", "HUF");

            // when: a proposal is created through the MCP expense tool
            Response response = callCreateExpenseProposal(token, requestBody);
            response.then().statusCode(200);
            assertThat(response.jsonPath().getBoolean("result.isError"))
                    .as("tool result isError")
                    .isNotEqualTo(Boolean.TRUE);

            // then: one ProposalCreated entry reaches ledger.cdc
            await("the proposal's fact reaches the stream").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            ChangeStreamEntries.entriesOnFor(STREAM_KEY, "ProposalCreated", userId))
                    .as("ProposalCreated entries for this user")
                    .hasSize(1));
            ChangeStreamEntry entry = ChangeStreamEntries.entriesOnFor(STREAM_KEY, "ProposalCreated", userId)
                    .get(0);

            // then: the payload carries the expenseId, status, message id, content and category/grouping
            assertThat(entry.payload().path("status").asText())
                    .as("payload.status")
                    .isEqualTo("PENDING");
            assertThat(entry.payload().path("incomingMessageId").asText())
                    .as("payload.incomingMessageId")
                    .isEqualTo(reference.value());
            assertThat(entry.payload().path("description").asText())
                    .as("payload.description")
                    .isEqualTo("Milk");
            assertThat(entry.payload().path("amount").asText())
                    .as("payload.amount")
                    .isEqualTo("7200.00");
            assertThat(entry.payload().path("category").path("name").asText())
                    .as("payload.category.name")
                    .isEqualTo("Supermarkets");
            assertThat(entry.payload().path("category").hasNonNull("id"))
                    .as("payload.category.id is present")
                    .isTrue();
            assertThat(entry.payload().path("grouping").path("name").asText())
                    .as("payload.grouping.name")
                    .isEqualTo("Groceries");
            assertThat(entry.payload().path("grouping").hasNonNull("id"))
                    .as("payload.grouping.id is present")
                    .isTrue();

            // then: the outbox holds no row afterwards - the reader's own delete never reaches the stream (A7)
            assertThat(OutboxRowUtils.outboxRowsFor(jdbcTemplate, userId))
                    .as("outbox rows for this user")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName(
                "when the caller belongs to nobody - then the tool call fails as today and nothing reaches the stream")
        void whenCallerBelongsToNobody_thenToolCallFailsAndNothingReachesStream() {
            long unknownUserId = Long.MAX_VALUE - 1;
            String token = McpTokens.tokenFor(accessTokenMinter, unknownUserId);

            String requestBody = McpRequests.createExpenseProposal(
                    "Supermarkets", "Groceries", "Milk", "Corner Shop", "7200", "HUF");

            // when: a proposal is created through the tool naming an id that names nobody
            Response response = callCreateExpenseProposal(token, requestBody);

            // then: the tool call fails with the error the endpoint answers today
            response.then().statusCode(200);
            assertThat(response.jsonPath().getBoolean("result.isError"))
                    .as("tool result isError")
                    .isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text"))
                    .as("tool error message")
                    .contains("the user is unknown");

            // then: no proposal is stored
            assertThat(ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, unknownUserId, ExpenseStatus.PENDING))
                    .as("PENDING expense rows for the unknown caller")
                    .isEmpty();

            // then: nothing reaches the stream for that id
            Optional<ChangeStreamEntry> published = ChangeStreamEntries.allEntriesOn(STREAM_KEY).stream()
                    .filter(entry -> unknownUserId == entry.userId())
                    .findFirst();
            assertThat(published)
                    .as("no entry published for the unknown caller")
                    .isEmpty();
        }
    }
}
