package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UniqueViolationsTest {

    private static final String CONSTRAINT = "ux_written_message_one_unsettled_per_user";

    @Nested
    @DisplayName("constraintName()")
    class ConstraintName {

        @Test
        @DisplayName("when the chain holds a unique violation naming a constraint - then that constraint is answered")
        void whenChainHoldsUniqueViolationNamingConstraint_thenConstraintIsAnswered() {
            SQLException violation = new SQLException(
                    "duplicate key value violates unique constraint \"" + CONSTRAINT + "\"", "23505");
            Throwable thrown = new RuntimeException("insert failed", new IllegalStateException("wrapped", violation));

            assertThat(UniqueViolations.constraintName(thrown)).isEqualTo(CONSTRAINT);
        }

        @Test
        @DisplayName("when the chain holds a violation of another kind - then null is answered")
        void whenChainHoldsViolationOfAnotherKind_thenNullIsAnswered() {
            SQLException violation = new SQLException(
                    "insert or update on table violates foreign key constraint \"fk_written_message_user\"", "23503");
            Throwable thrown = new RuntimeException("insert failed", violation);

            assertThat(UniqueViolations.constraintName(thrown)).isNull();
        }

        @Test
        @DisplayName("when the chain holds no SQLException - then null is answered")
        void whenChainHoldsNoSqlException_thenNullIsAnswered() {
            Throwable thrown = new RuntimeException("insert failed", new IllegalStateException("wrapped"));

            assertThat(UniqueViolations.constraintName(thrown)).isNull();
        }

        @Test
        @DisplayName("when the unique violation carries no message - then null is answered")
        void whenUniqueViolationCarriesNoMessage_thenNullIsAnswered() {
            SQLException violation = new SQLException((String) null, "23505");
            Throwable thrown = new RuntimeException("insert failed", violation);

            assertThat(UniqueViolations.constraintName(thrown)).isNull();
        }
    }
}
