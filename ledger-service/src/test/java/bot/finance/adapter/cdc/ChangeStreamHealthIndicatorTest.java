package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class ChangeStreamHealthIndicatorTest {

    private ChangeStreamReader changeStreamReader;
    private ChangeStreamHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        changeStreamReader = mock(ChangeStreamReader.class);
        healthIndicator = new ChangeStreamHealthIndicator(changeStreamReader);
    }

    @Nested
    @DisplayName("checking health")
    class HealthCheck {

        @Test
        @DisplayName("when the reader reports STREAMING - then the component is UP and the detail names STREAMING")
        void whenReaderReportsStreaming_thenComponentIsUpAndDetailNamesStreaming() {
            when(changeStreamReader.state()).thenReturn(ChangeStreamState.STREAMING);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsValue("STREAMING");
        }

        @Test
        @DisplayName("when the reader reports STANDBY - then the component is UP")
        void whenReaderReportsStandby_thenComponentIsUp() {
            when(changeStreamReader.state()).thenReturn(ChangeStreamState.STANDBY);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }

        @Test
        @DisplayName("when the reader reports DOWN - then the component is DOWN and the detail names DOWN")
        void whenReaderReportsDown_thenComponentIsDownAndDetailNamesDown() {
            when(changeStreamReader.state()).thenReturn(ChangeStreamState.DOWN);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsValue("DOWN");
        }
    }
}
