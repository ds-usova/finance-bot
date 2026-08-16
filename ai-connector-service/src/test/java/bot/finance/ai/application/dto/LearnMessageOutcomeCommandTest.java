package bot.finance.ai.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.common.fixtures.RecordedChangeFixtures;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.RecordedChange;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LearnMessageOutcomeCommandTest {

    private static final String DELIVERY_ID = "delivery-1";

    @Nested
    @DisplayName("constructing the command")
    class Construction {

        @Test
        @DisplayName("when a delivery id and a change are given - then both read back unchanged")
        void whenDeliveryIdAndChangeGiven_thenBothReadBackUnchanged() {
            RecordedChange change = RecordedChangeFixtures.proposalCreated();

            LearnMessageOutcomeCommand command = new LearnMessageOutcomeCommand(DELIVERY_ID, change);

            assertThat(command.deliveryId()).isEqualTo(DELIVERY_ID);
            assertThat(command.change()).isEqualTo(change);
        }

        @ParameterizedTest
        @MethodSource("invalidDeliveryIdOrChange")
        @DisplayName(
                "when the delivery id is null or blank, or the change is null - then throws " + "InvalidValueException")
        void whenDeliveryIdNullOrBlankOrChangeIsNull_thenThrowsInvalidValueException(
                String deliveryId, RecordedChange change) {
            assertThatThrownBy(() -> new LearnMessageOutcomeCommand(deliveryId, change))
                    .isInstanceOf(InvalidValueException.class);
        }

        private static Stream<Arguments> invalidDeliveryIdOrChange() {
            RecordedChange validChange = RecordedChangeFixtures.proposalCreated();
            return Stream.of(
                    Arguments.of(null, validChange),
                    Arguments.of("", validChange),
                    Arguments.of("   ", validChange),
                    Arguments.of(DELIVERY_ID, null));
        }
    }
}
