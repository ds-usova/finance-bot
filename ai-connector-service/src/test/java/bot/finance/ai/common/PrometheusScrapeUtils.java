package bot.finance.ai.common;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the actuator's rendered Prometheus text back, and one sample out of it.
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

    /**
     * The value of the sample named {@code metricName} whose labels include every one of {@code labels}, each
     * written {@code name="value"} — or its untagged sample when no label is given. Zero when the scrape holds no
     * such sample, since a tagged meter appears only on its first use.
     */
    public static double sampleOrZero(String body, String metricName, String... labels) {
        for (String line : body.split("\n")) {
            if (labels.length == 0 && line.startsWith(metricName + " ")) {
                return value(line);
            }
            if (labels.length > 0
                    && line.startsWith(metricName + "{")
                    && Arrays.stream(labels).allMatch(line.substring(0, line.indexOf('}'))::contains)) {
                return value(line);
            }
        }
        return 0.0;
    }

    private static double value(String line) {
        return Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1));
    }
}
