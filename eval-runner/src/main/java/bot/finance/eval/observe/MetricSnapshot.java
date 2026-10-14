package bot.finance.eval.observe;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
            if (nameOf(key).equals(name) && matches(labelsOf(key), labels)) {
                total += entry.getValue();
            }
        }
        return total;
    }

    public Set<String> labelValues(String name, String label) {
        Set<String> values = new LinkedHashSet<>();
        for (String key : series.keySet()) {
            String value = labelsOf(key).get(label);
            if (nameOf(key).equals(name) && value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private static String nameOf(String key) {
        int brace = key.indexOf('{');
        return brace >= 0 ? key.substring(0, brace) : key;
    }

    private static Map<String, String> labelsOf(String key) {
        Map<String, String> labels = new LinkedHashMap<>();
        int brace = key.indexOf('{');
        if (brace >= 0) {
            Matcher matcher = LABEL.matcher(key.substring(brace));
            while (matcher.find()) {
                labels.put(matcher.group(1), matcher.group(2));
            }
        }
        return labels;
    }

    private static boolean matches(Map<String, String> present, Map<String, String> wanted) {
        return wanted.entrySet().stream()
                .allMatch(want -> !present.containsKey(want.getKey())
                        || present.get(want.getKey()).equals(want.getValue()));
    }
}
