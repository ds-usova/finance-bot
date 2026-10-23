package bot.finance.eval.ledger;

import bot.finance.eval.EvalException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends JSON to the ledger as one browser session would: it carries the session's cookies, echoes the CSRF token
 * on every write, and refuses an answer whose status the caller did not expect.
 */
final class LedgerHttp {

    static final String CSRF_COOKIE = "XSRF-TOKEN";

    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final JsonMapper JSON = new JsonMapper();

    private final HttpClient http;
    private final String baseUrl;
    private final CookieJar cookies = new CookieJar();

    LedgerHttp(HttpClient http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    JsonNode get(String stage, String path, Set<Integer> expected) {
        return send(stage, "GET", path, null, null, expected);
    }

    /** Writes {@code body} as JSON under {@code contentType}. */
    JsonNode send(String stage, String method, String path, Object body, String contentType, Set<Integer> expected) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .method(
                        method,
                        body == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        cookies.header().ifPresent(header -> request.header("Cookie", header));
        if (!method.equals("GET")) {
            cookies.get(CSRF_COOKIE).ifPresent(token -> request.header(CSRF_HEADER, token));
        }

        HttpResponse<String> response = execute(stage, method, path, request.build());
        cookies.keep(response.headers().allValues("Set-Cookie"));
        if (!expected.contains(response.statusCode())) {
            throw new EvalException(stage, method + " " + path + " answered " + response.body(), response.statusCode());
        }
        return response.body().isBlank() ? JSON.missingNode() : JSON.readTree(response.body());
    }

    boolean hasCookie(String name) {
        return cookies.get(name).isPresent();
    }

    private HttpResponse<String> execute(String stage, String method, String path, HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new EvalException(stage, method + " " + path + " failed: " + e.getMessage(), null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EvalException(stage, method + " " + path + " was interrupted", null, e);
        }
    }
}
