package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import bot.finance.adapter.persistence.SpendingQueryEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.IncomingMessages;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.fixtures.TurnOperationCalls;
import bot.finance.common.rows.SpendingQueryRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;

/**
 * Drives {@code LedgerTurnService.RecordSpendingPeriod} on the ledger's gRPC port end to end against the fully
 * wired application.
 */
class RecordSpendingPeriodSystemTest extends AbstractSystemTest {

    private static final String DAY = "2026-10-12";

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Autowired
    private GrpcServerLifecycle grpcServerLifecycle;

    private RecordSpendingPeriodResponse recordPeriod(String token) {
        return TurnOperationCalls.call(
                grpcServerLifecycle.getPort(),
                token,
                stub -> stub.recordSpendingPeriod(TurnOperationCalls.recordPeriod(DAY, DAY)));
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a period is recorded for a Telegram message - then both days are answered and the query "
                + "is stored")
        void whenPeriodIsRecorded_thenBothDaysAreAnsweredAndQueryIsStored() {
            // given: a stored person and a token the application minted for their Telegram message
            long userId = UserRowUtils.storedUserId(userEntityRepository, "record-spending-period-happy-path-user");
            IncomingMessageId reference = IncomingMessages.newIncomingMessageId();
            String token = McpTokens.tokenFor(accessTokenMinter, userId, reference);

            // when: RecordSpendingPeriod is called for 2026-10-12 to 2026-10-12
            RecordSpendingPeriodResponse answer = recordPeriod(token);

            // then: the call answers both days
            assertThat(answer.getFrom()).as("answered first day").isEqualTo(DAY);
            assertThat(answer.getTo()).as("answered last day").isEqualTo(DAY);

            // then: a spending query row for that day is stored under the message
            List<SpendingQueryEntity> rows = SpendingQueryRowUtils.spendingQueryRowsFor(jdbcAggregateTemplate, userId);
            assertThat(rows)
                    .as("stored spending_query rows for user %s", userId)
                    .hasSize(1);
            SpendingQueryEntity row = rows.get(0);
            assertThat(row.incomingMessageId())
                    .as("stored query's message reference")
                    .isEqualTo(reference.value());
            assertThat(row.periodStart()).as("stored query's period start").isEqualTo(LocalDate.parse(DAY));
            assertThat(row.periodEnd()).as("stored query's period end").isEqualTo(LocalDate.parse(DAY));
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the token's subject is no stored user - then the call fails as unknown and nothing is "
                + "stored")
        void whenTokenSubjectIsNoStoredUser_thenCallFailsAsUnknownAndNothingIsStored() {
            // given: a token whose subject no stored user carries
            long unknownUserId = Long.MAX_VALUE - 7;
            String token = McpTokens.tokenFor(accessTokenMinter, unknownUserId);

            // when: RecordSpendingPeriod is called
            Throwable thrown = catchThrowable(() -> recordPeriod(token));

            // then: the call fails with NOT_FOUND saying the user is unknown
            assertThat(thrown)
                    .as("the call's failure")
                    .isInstanceOfSatisfying(StatusRuntimeException.class, failure -> {
                        assertThat(failure.getStatus().getCode())
                                .as("status code")
                                .isEqualTo(Status.Code.NOT_FOUND);
                        assertThat(failure.getStatus().getDescription())
                                .as("status description")
                                .containsIgnoringCase("unknown");
                    });

            // then: no row is stored
            assertThat(SpendingQueryRowUtils.spendingQueryRowsFor(jdbcAggregateTemplate, unknownUserId))
                    .as("stored spending_query rows for user %s", unknownUserId)
                    .isEmpty();
        }
    }
}
