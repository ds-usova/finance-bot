package bot.finance.eval.observe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One scrape of a Prometheus text endpoint, keyed by the series as written: name and labels. */
public record MetricSnapshot(Map<String, Double> series) {

    private static final Pattern LABEL = Pattern.compile("(\\w+)=\"((?:[^\"\\\\]|\\\\.)*)\"");

    public static MetricSnapshot parse(String body) {
        Map<String, Double> series = new LinkedHashMap<>();
        for (String line : body.split("\n")) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int close = line.lastIndexOf('}');
            int split = close >= 0 ? close + 1 : line.indexOf(' ');
            if (split <= 0) {
                continue;
            }
            String key = line.substring(0, split).strip();
            String value = line.substring(split).strip().split("\\s+")[0];
            try {
                series.put(key, Double.parseDouble(value));
            } catch (NumberFormatException ignored) {
                // a sample this runner cannot read is not one it sums
            }
        }
        return new MetricSnapshot(series);
    }

    /**
     * The sum over every series of {@code name} whose labels agree with {@code labels}. A label the series does not
     * carry does not exclude it, so a filter on an operation still counts a meter that names none.
     */
    public double sum(String name, Map<String, String> labels) {
        double total = 0;
        for (Map.Entry<String, Double> entry : series.entrySet()) {
            String key = entry.getKey();
            int brace = key.indexOf('{');
            String seriesName = brace >= 0 ? key.substring(0, brace) : key;
            if (seriesName.equals(name) && matches(brace >= 0 ? key.substring(brace) : "", labels)) {
                total += entry.getValue();
            }
        }
        return total;
    }

    private static boolean matches(String labelText, Map<String, String> wanted) {
        Map<String, String> present = new LinkedHashMap<>();
        Matcher matcher = LABEL.matcher(labelText);
        while (matcher.find()) {
            present.put(matcher.group(1), matcher.group(2));
        }
        return wanted.entrySet().stream()
                .allMatch(want -> !present.containsKey(want.getKey())
                        || present.get(want.getKey()).equals(want.getValue()));
    }
}
