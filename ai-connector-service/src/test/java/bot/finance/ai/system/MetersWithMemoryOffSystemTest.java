package bot.finance.ai.system;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.common.boot.AbstractSystemTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers {@code GET /actuator/prometheus} with the memory off — {@link AbstractSystemTest}'s test profile default
 * — where {@code MicrometerRecallMeters}, {@code MicrometerChangeStreamMeters} and {@code RedisPendingEntries}
 * carry {@code @ConditionalOnProperty(name = "memory.enabled", havingValue = "true")} and so are never registered.
 */
class MetersWithMemoryOffSystemTest extends AbstractSystemTest {

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when prometheus is scraped with the memory off - then no ai_recall or ai_cdc meter appears")
        void whenPrometheusIsScrapedWithMemoryOff_thenNoAiRecallOrAiCdcMeterAppears() {
            Response response = given().port(actuatorPort).when().get("/actuator/prometheus");
            log.info("response length: {}", response.getBody().asString().length());

            response.then().statusCode(200);
            String body = response.getBody().asString();

            assertThat(body).as("no recall meter").doesNotContain("ai_recall_");
            assertThat(body).as("no change-stream meter").doesNotContain("ai_cdc_");
        }
    }
}
