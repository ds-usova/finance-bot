package bot.finance.common.fixtures;

import io.restassured.RestAssured;

/**
 * Reads a meter's rendered value back off {@code /actuator/prometheus} on the management port, the way the
 * metrics collector sees it - so a scrape test asserts against the rendered name and tags rather than against the
 * registry the adapter wrote into.
 */
public class PrometheusScrapes {

    private static final String SCRAPE_PATH = "/actuator/prometheus";

    private PrometheusScrapes() {}

    /**
     * The value the scrape renders for {@code meter} under {@code tags}, written the way the exposition format
     * writes them - {@code name="value"} pairs in the order Prometheus sorts them, comma-separated and unbraced.
     * Answers {@code 0.0} when no such line is rendered, so a test reading a counter's growth needs no meter to
     * exist yet.
     */
    public static double value(int managementPort, String meter, String tags) {
        String scrape =
                RestAssured.given().port(managementPort).when().get(SCRAPE_PATH).asString();
        String line = meter + "{" + tags + "}";
        return scrape.lines()
                .filter(rendered -> rendered.startsWith(line))
                .mapToDouble(rendered -> Double.parseDouble(rendered.substring(rendered.lastIndexOf(' ') + 1)))
                .findFirst()
                .orElse(0.0);
    }
}
