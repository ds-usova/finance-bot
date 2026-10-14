package bot.finance.adapter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.TurnChannel;
import bot.finance.common.boot.MetricsAdapterTest;
import bot.finance.common.fixtures.PrometheusScrapes;
import io.restassured.RestAssured;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;

/**
 * Drives the turn meter port and asserts what a prometheus scrape actually renders - the outcome names, the
 * {@code UNREPORTED} tag, and the {@code ACCEPT}/{@code DISCARD} to {@code accepted}/{@code discarded} mapping.
 * The resolved-counter tests read the counter's growth rather than its value, since the context and its
 * registry are shared across every {@link MetricsAdapterTest} class.
 */
@MetricsAdapterTest
class MicrometerTurnMetersTest {

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private MicrometerTurnMeters meters;

    @Nested
    @DisplayName("countTurn()")
    class CountTurn {

        @ParameterizedTest(name = "{0}")
        @EnumSource(ReportOutcome.class)
        @DisplayName("when a turn is counted under an outcome - then the scrape renders the counter under that "
                + "outcome's name")
        void whenTurnCountedUnderOutcome_thenScrapeRendersCounterUnderThatOutcomeName(ReportOutcome outcome) {
            meters.countTurn(outcome);

            assertThat(scrapedValue(MeterName.TURNS.meterName(), "outcome=\"" + outcome.name() + "\""))
                    .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("countUnreported()")
    class CountUnreported {

        @Test
        @DisplayName("when an unreported turn is counted - then the scrape renders the counter under UNREPORTED")
        void whenUnreportedTurnCounted_thenScrapeRendersCounterUnderUnreported() {
            meters.countUnreported();

            assertThat(scrapedValue(MeterName.TURNS.meterName(), "outcome=\"UNREPORTED\""))
                    .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("countResolved()")
    class CountResolved {

        @Test
        @DisplayName("when two proposals are counted resolved as ACCEPT - then the accepted counter grows by two")
        void whenTwoProposalsCountedResolvedAsAccept_thenAcceptedCounterGrowsByTwo() {
            double before = scrapedValue(MeterName.PROPOSALS_RESOLVED.meterName(), "resolution=\"accepted\"");

            meters.countResolved(ProposalResolution.ACCEPT, 2);

            double after = scrapedValue(MeterName.PROPOSALS_RESOLVED.meterName(), "resolution=\"accepted\"");
            assertThat(after - before).isEqualTo(2.0);
        }

        @Test
        @DisplayName("when a proposal is counted resolved as DISCARD - then the scrape renders it under discarded")
        void whenProposalCountedResolvedAsDiscard_thenScrapeRendersItUnderDiscarded() {
            double before = scrapedValue(MeterName.PROPOSALS_RESOLVED.meterName(), "resolution=\"discarded\"");

            meters.countResolved(ProposalResolution.DISCARD, 1);

            double after = scrapedValue(MeterName.PROPOSALS_RESOLVED.meterName(), "resolution=\"discarded\"");
            assertThat(after - before).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("countSwept()")
    class CountSwept {

        @Test
        @DisplayName(
                "when an incoming turn and two written turns are counted swept - then each channel's counter grows")
        void whenIncomingAndWrittenTurnsCountedSwept_thenEachChannelsCounterGrows() {
            double telegramBefore = scrapedValue(MeterName.TURNS_SWEPT.meterName(), "channel=\"telegram\"");
            double webBefore = scrapedValue(MeterName.TURNS_SWEPT.meterName(), "channel=\"web\"");

            meters.countSwept(TurnChannel.INCOMING);
            meters.countSwept(TurnChannel.WRITTEN);
            meters.countSwept(TurnChannel.WRITTEN);

            double telegramAfter = scrapedValue(MeterName.TURNS_SWEPT.meterName(), "channel=\"telegram\"");
            double webAfter = scrapedValue(MeterName.TURNS_SWEPT.meterName(), "channel=\"web\"");
            assertThat(telegramAfter - telegramBefore).isEqualTo(1.0);
            assertThat(webAfter - webBefore).isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("recordOldestQueued()")
    class RecordOldestQueued {

        @Test
        @DisplayName("when 90 seconds and then 30 seconds are recorded - then the gauge reads the latest")
        void whenNinetyThenThirtySecondsRecorded_thenGaugeReadsTheLatest() {
            meters.recordOldestQueued(TurnChannel.INCOMING, Duration.ofSeconds(90));
            meters.recordOldestQueued(TurnChannel.INCOMING, Duration.ofSeconds(30));

            assertThat(scrapedValue(MeterName.TURN_QUEUE_OLDEST.meterName(), "channel=\"telegram\""))
                    .isEqualTo(30.0);
        }

        @Test
        @DisplayName("when zero is recorded for the written channel - then the gauge reads zero")
        void whenZeroRecordedForWrittenChannel_thenGaugeReadsZero() {
            meters.recordOldestQueued(TurnChannel.WRITTEN, Duration.ofSeconds(45));
            meters.recordOldestQueued(TurnChannel.WRITTEN, Duration.ZERO);

            String line = MeterName.TURN_QUEUE_OLDEST.meterName() + "{channel=\"web\"}";
            String scrape = RestAssured.given()
                    .port(managementPort)
                    .when()
                    .get("/actuator/prometheus")
                    .asString();

            assertThat(scrape.lines().filter(rendered -> rendered.startsWith(line)))
                    .singleElement()
                    .asString()
                    .endsWith(" 0.0");
        }
    }

    private double scrapedValue(String meter, String tags) {
        return PrometheusScrapes.value(managementPort, meter, tags);
    }
}
