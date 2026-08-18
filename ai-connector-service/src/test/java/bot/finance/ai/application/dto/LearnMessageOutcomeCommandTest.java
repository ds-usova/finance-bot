package bot.finance.ai.application.dto;

import static bot.finance.ai.common.fixtures.SpendingFactFixtures.position;
import static bot.finance.ai.common.fixtures.SpendingFactFixtures.spendingRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.RecordedStatus;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.StreamPosition;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LearnMessageOutcomeCommandTest {

    private static final String DELIVERY_ID = "delivery-1";
    private static final StreamPosition POSITION = position();
    private static final RecordedStatus STATUS = RecordedStatus.ACCEPTED;
    private static final SpendingRow ENTRY = spendingRow();

    @Nested
    @DisplayName("constructing the command")
    class Construction {

        @Test
        @DisplayName("when a delivery id, a position, a status and an entry are given - then all four read "
                + "back unchanged")
        void whenAllFourComponentsGiven_thenAllFourReadBackUnchanged() {
            LearnMessageOutcomeCommand command = new LearnMessageOutcomeCommand(DELIVERY_ID, POSITION, STATUS, ENTRY);

            assertThat(command.deliveryId()).isEqualTo(DELIVERY_ID);
            assertThat(command.position()).isEqualTo(POSITION);
            assertThat(command.status()).isEqualTo(STATUS);
            assertThat(command.entry()).isEqualTo(ENTRY);
        }

        static Stream<Arguments> invalidComponents() {
            return Stream.of(
                    Arguments.of(null, POSITION, STATUS, ENTRY),
                    Arguments.of(" ", POSITION, STATUS, ENTRY),
                    Arguments.of(DELIVERY_ID, null, STATUS, ENTRY),
                    Arguments.of(DELIVERY_ID, POSITION, null, ENTRY),
                    Arguments.of(DELIVERY_ID, POSITION, STATUS, null));
        }

        @ParameterizedTest
        @MethodSource("invalidComponents")
        @DisplayName("when the delivery id is null or blank, or the position, status or entry is null - then "
                + "throws InvalidValueException")
        void whenDeliveryIdNullOrBlankOrPositionStatusEntryIsNull_thenThrowsInvalidValueException(
                String deliveryId, StreamPosition position, RecordedStatus status, SpendingRow entry) {
            assertThatThrownBy(() -> new LearnMessageOutcomeCommand(deliveryId, position, status, entry))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
