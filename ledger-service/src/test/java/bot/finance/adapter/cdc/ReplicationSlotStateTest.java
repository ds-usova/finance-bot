package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReplicationSlotStateTest {

    @Nested
    @DisplayName("mapping a wal_status text to a state")
    class FromWalStatus {

        @ParameterizedTest(name = "{0} maps to {1}")
        @CsvSource({"reserved,RESERVED", "extended,EXTENDED", "unreserved,UNRESERVED", "lost,LOST"})
        @DisplayName(
                "when a documented wal_status is mapped - then the matching state and its ordinal are " + "answered")
        void whenDocumentedWalStatusIsMapped_thenMatchingStateAndItsOrdinalAreAnswered(
                String walStatus, ReplicationSlotState expected) {
            ReplicationSlotState result = ReplicationSlotState.fromWalStatus(walStatus);

            assertThat(result).isEqualTo(expected);
            assertThat(result.ordinal()).isEqualTo(expected.ordinal());
        }

        @Test
        @DisplayName(
                "when no slot row exists at all - then ABSENT's ordinal is distinct from every wal_status " + "ordinal")
        void whenNoSlotRowExistsAtAll_thenAbsentOrdinalDistinctFromEveryWalStatusOrdinal() {
            assertThat(ReplicationSlotState.ABSENT.ordinal())
                    .isNotIn(
                            ReplicationSlotState.RESERVED.ordinal(),
                            ReplicationSlotState.EXTENDED.ordinal(),
                            ReplicationSlotState.UNRESERVED.ordinal(),
                            ReplicationSlotState.LOST.ordinal());
        }

        @Test
        @DisplayName("when the wal_status text is one Postgres does not document - then it is refused rather than "
                + "silently mapped")
        void whenWalStatusTextIsUndocumented_thenRefusedRatherThanSilentlyMapped() {
            assertThatThrownBy(() -> ReplicationSlotState.fromWalStatus("materializing"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
