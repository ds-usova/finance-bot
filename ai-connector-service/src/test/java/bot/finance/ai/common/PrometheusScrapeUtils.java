package bot.finance.ai.common;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the actuator's rendered Prometheus text back, and one untagged sample line out of it.
 */
public final class PrometheusScrapeUtils {

    private PrometheusScrapeUtils() {}

    public static String scrape(int managementPort) {
        return given().port(managementPort)
                .when()
                .get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .extract()
                .asString();
    }

    public static double sample(String body, String metricName) {
        Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(metricName) + "\\s+(\\S+)$")
                .matcher(body);
        assertThat(matcher.find()).as(metricName + " sample present").isTrue();
        return Double.parseDouble(matcher.group(1));
    }
}
