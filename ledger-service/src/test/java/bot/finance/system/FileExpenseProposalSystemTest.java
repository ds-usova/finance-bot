package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import bot.finance.adapter.persistence.ExpenseEntity;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.application.port.UserRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.IncomingMessages;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.fixtures.TurnOperationCalls;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.domain.model.User;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Grouping;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;

/**
 * Drives {@code LedgerTurnService.FileExpenseProposal} on the ledger's gRPC port end to end against the fully wired
 * application. The person and their category tree are seeded through the wired {@link UserRepository} with
 * {@link Grouping#defaults()}, the tree's only writer.
 */
class FileExpenseProposalSystemTest extends AbstractSystemTest {

    private static final String GROUPING = "Dining";
    private static final String CATEGORY = "Cafés";
    private static final String DESCRIPTION = "coffee";
    private static final String AMOUNT = "4.50";
    private static final String CURRENCY_CODE = "EUR";
    private static final String OPERATION_PATH = "/bot.finance.ledger.v1.LedgerTurnService/FileExpenseProposal";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Autowired
    private GrpcServerLifecycle grpcServerLifecycle;

    private long storedPersonId(String externalId) {
        User user = userRepository.create(User.newUser(externalId), Grouping.defaults());
        return user.id().orElseThrow();
    }

    private FileExpenseProposalResponse fileExpense(String token, String grouping, String category) {
        return TurnOperationCalls.call(
                grpcServerLifecycle.getPort(),
                token,
                stub -> stub.fileExpenseProposal(TurnOperationCalls.fileExpense(
                        grouping, category, DESCRIPTION, null, AMOUNT, CURRENCY_CODE)));
    }

    private List<ExpenseEntity> pendingRows(long userId) {
        return ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId, ExpenseStatus.PENDING);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a person files a category under its own grouping - then the stored entry is answered")
        void whenCategoryIsFiledUnderItsGrouping_thenStoredEntryIsAnswered() {
            // given: a stored person and a token the application minted for their message
            long userId = storedPersonId("file-expense-proposal-happy-path-user");
            IncomingMessageId reference = IncomingMessages.newIncomingMessageId();
            String token = McpTokens.tokenFor(accessTokenMinter, userId, reference);

            // when: FileExpenseProposal is called on the gRPC port
            FileExpenseProposalResponse answer = fileExpense(token, GROUPING, CATEGORY);

            // then: the call answers the stored entry
            List<ExpenseEntity> rows = pendingRows(userId);
            assertThat(rows)
                    .as("stored PENDING expense rows for user %s", userId)
                    .hasSize(1);
            ExpenseEntity row = rows.get(0);
            assertThat(answer.getId()).as("answered id is the stored row's id").isEqualTo(row.id());
            assertThat(answer.getGrouping()).as("answered grouping").isEqualTo(GROUPING);
            assertThat(answer.getCategory()).as("answered category").isEqualTo(CATEGORY);
            assertThat(answer.getDescription()).as("answered description").isEqualTo(DESCRIPTION);
            assertThat(answer.hasMerchant()).as("answered merchant is absent").isFalse();
            assertThat(answer.getAmount()).as("answered amount").isEqualTo(AMOUNT);
            assertThat(answer.getCurrencyCode()).as("answered currency").isEqualTo(CURRENCY_CODE);

            // then: one pending expense row is stored under that message
            assertThat(row.incomingMessageId())
                    .as("stored proposal's incoming message id")
                    .isEqualTo(reference.value());
            assertThat(row.description()).as("stored proposal's description").isEqualTo(DESCRIPTION);
            assertThat(row.amountMinorUnits()).as("stored proposal's minor units").isEqualTo(450L);
            assertThat(row.currencyCode()).as("stored proposal's currency code").isEqualTo(CURRENCY_CODE);
        }

        @Test
        @DisplayName("when a call has been answered - then the scrape shows the server's call timer as OK")
        void whenCallHasBeenAnswered_thenScrapeShowsServerCallTimerAsOk() {
            // given: one call answered
            long userId = storedPersonId("file-expense-proposal-meter-user");
            fileExpense(McpTokens.tokenFor(accessTokenMinter, userId), GROUPING, CATEGORY);

            // when: the management port's Prometheus scrape is read
            String scrape = RestAssured.given()
                    .port(managementPort)
                    .when()
                    .get("/actuator/prometheus")
                    .asString();

            // then: it shows the server's call timer for FileExpenseProposal with status OK
            assertThat(scrape.lines())
                    .as("scrape lines of the gRPC server's call timer")
                    .anySatisfy(line -> assertThat(line)
                            .startsWith("grpc_server")
                            .contains("FileExpenseProposal")
                            .contains("OK"));
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the person has no such grouping - then the call fails naming it and nothing is stored")
        void whenPersonHasNoSuchGrouping_thenCallFailsNamingItAndNothingIsStored() {
            // given: a stored person with no grouping Snacks
            long userId = storedPersonId("file-expense-proposal-unknown-grouping-user");
            String token = McpTokens.tokenFor(accessTokenMinter, userId);

            // when: FileExpenseProposal is called for Snacks
            Throwable thrown = catchThrowable(() -> fileExpense(token, "Snacks", CATEGORY));

            // then: the call fails with INVALID_ARGUMENT repeating Snacks
            assertThat(thrown)
                    .as("the call's failure")
                    .isInstanceOfSatisfying(StatusRuntimeException.class, failure -> {
                        assertThat(failure.getStatus().getCode())
                                .as("status code")
                                .isEqualTo(Status.Code.INVALID_ARGUMENT);
                        assertThat(failure.getStatus().getDescription())
                                .as("status description")
                                .contains("Snacks");
                    });

            // then: no row is stored
            assertThat(pendingRows(userId))
                    .as("stored PENDING expense rows for user %s", userId)
                    .isEmpty();
        }

        @Test
        @DisplayName("when the operation's path is posted to the HTTP port - then it is not answered and nothing is "
                + "stored")
        void whenOperationPathIsPostedToHttpPort_thenItIsNotAnsweredAndNothingIsStored() {
            // given: a valid token
            long userId = storedPersonId("file-expense-proposal-http-user");
            String token = McpTokens.tokenFor(accessTokenMinter, userId);

            // when: the operation's path is posted to the HTTP port as a gRPC call would be
            Response response = RestAssured.given()
                    .port(port)
                    .contentType("application/grpc")
                    .header("Authorization", "Bearer " + token)
                    .body(new byte[0])
                    .when()
                    .post(OPERATION_PATH);
            logResponse(response);

            // then: the operation does not answer it
            assertThat(response.statusCode()).as("HTTP status").isGreaterThanOrEqualTo(400);

            // then: no row is stored
            assertThat(pendingRows(userId))
                    .as("stored PENDING expense rows for user %s", userId)
                    .isEmpty();
        }
    }
}
