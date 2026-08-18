package bot.finance.ai.application.dto;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LearnMessageOutcomeCommandTest {

    @Nested
    @DisplayName("constructing the command")
    class Construction {

        @Disabled("RU04: rewritten against the deliveryId, position, status and entry components")
        @Test
        @DisplayName("when a delivery id and a change are given - then both read back unchanged")
        void whenDeliveryIdAndChangeGiven_thenBothReadBackUnchanged() {}

        @Disabled("RU04: rewritten against a null or blank deliveryId, or a null position, status or entry")
        @Test
        @DisplayName(
                "when the delivery id is null or blank, or the change is null - then throws " + "InvalidValueException")
        void whenDeliveryIdNullOrBlankOrChangeIsNull_thenThrowsInvalidValueException() {}
    }
}
