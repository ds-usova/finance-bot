package bot.finance.eval.observe;

import bot.finance.eval.EvalException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Scrapes a service's {@code /actuator/prometheus} directly; Prometheus's own scrape interval is too coarse. */
public final class Prometheus {

    private final HttpClient http;

    public Prometheus(HttpClient http) {
        this.http = http;
    }

    public MetricSnapshot scrape(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new EvalException("metrics", url + " answered " + response.statusCode(), response.statusCode());
            }
            return MetricSnapshot.parse(response.body());
        } catch (IOException e) {
            throw new EvalException("metrics", "cannot scrape " + url + ": " + e.getMessage(), null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EvalException("metrics", "scrape of " + url + " was interrupted", null, e);
        }
    }
}
