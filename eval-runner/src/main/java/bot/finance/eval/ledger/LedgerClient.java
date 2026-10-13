package bot.finance.eval.ledger;

import bot.finance.eval.EvalException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One signed-in person's view of the ledger's REST API. Cookies are kept by hand, since the session cookie may be
 * Secure while the ledger is reached over plain http.
 */
public final class LedgerClient {

    private static final JsonMapper JSON = new JsonMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int PAGE_SIZE = 100;

    private final HttpClient http;
    private final String baseUrl;
    private final Map<String, String> cookies = new LinkedHashMap<>();

    public LedgerClient(HttpClient http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void signIn(long telegramId, String botToken) {
        // An unauthenticated GET is answered 401 and still sets the CSRF cookie the sign-in needs.
        send("setup", "GET", "/api/v1/session", null, null, Set.of(200, 401));
        if (!cookies.containsKey("XSRF-TOKEN")) {
            throw new EvalException("setup", "GET /api/v1/session set no XSRF-TOKEN cookie");
        }
        Map<String, String> payload =
                TelegramLogin.payload(telegramId, Instant.now().getEpochSecond(), botToken);
        send("setup", "POST", "/api/v1/session", JSON.writeValueAsString(payload), "application/json", Set.of(200));
    }

    public void setDefaultCurrency(String currency) {
        send(
                "setup",
                "PUT",
                "/api/v1/preferences",
                JSON.writeValueAsString(Map.of("defaultCurrency", currency)),
                "application/json",
                Set.of(200));
    }

    public long pendingTotal() {
        return send("setup", "GET", "/api/v1/expenses?status=PENDING&limit=1", null, null, Set.of(200))
                .path("total")
                .asLong();
    }

    public long fileMessage(String stage, String text) {
        JsonNode body = send(
                stage,
                "POST",
                "/api/v1/messages",
                JSON.writeValueAsString(Map.of("text", text)),
                "application/json",
                Set.of(202));
        return body.path("id").asLong();
    }

    public WrittenMessage message(String stage, long id) {
        JsonNode body = send(stage, "GET", "/api/v1/messages/" + id, null, null, Set.of(200));
        JsonNode outcome = body.path("outcome");
        return new WrittenMessage(id, body.path("state").asString(), outcome.isNull() ? null : outcome.asString(null));
    }

    /** Every pending entry, read page by page; a page that disagrees with the first one's total is an error. */
    public List<JsonNode> allPending(String stage) {
        List<JsonNode> items = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Long total = null;
        int offset = 0;
        while (total == null || items.size() < total) {
            JsonNode page = send(
                    stage,
                    "GET",
                    "/api/v1/expenses?status=PENDING&limit=" + PAGE_SIZE + "&offset=" + offset,
                    null,
                    null,
                    Set.of(200));
            long pageTotal = page.path("total").asLong();
            if (total != null && pageTotal != total) {
                throw new EvalException(stage, "pending total changed from " + total + " to " + pageTotal);
            }
            total = pageTotal;
            JsonNode pageItems = page.path("items");
            if (pageItems.isEmpty() && items.size() < total) {
                throw new EvalException(stage, "empty page at offset " + offset + " before " + total + " entries");
            }
            for (JsonNode item : pageItems) {
                if (!seen.add(item.path("id").asLong())) {
                    throw new EvalException(stage, "pending entry " + item.path("id") + " answered twice");
                }
                items.add(item);
            }
            offset += pageItems.size();
        }
        return items;
    }

    public List<Category> categories(String stage) {
        List<Category> categories = new ArrayList<>();
        for (JsonNode node : send(stage, "GET", "/api/v1/categories", null, null, Set.of(200))) {
            categories.add(new Category(
                    node.path("id").asLong(),
                    node.path("name").asString(),
                    node.path("groupingId").asLong(),
                    node.path("groupingName").asString()));
        }
        return categories;
    }

    public void changeCategory(String stage, long expenseId, long categoryId) {
        String patch =
                JSON.writeValueAsString(List.of(Map.of("op", "replace", "path", "/categoryId", "value", categoryId)));
        send(stage, "PATCH", "/api/v1/expenses/" + expenseId, patch, "application/json-patch+json", Set.of(200));
    }

    public void accept(String stage, List<Long> ids) {
        resolve(stage, "/api/v1/expenses/acceptances", "accepted", ids);
    }

    public void discard(String stage, List<Long> ids) {
        resolve(stage, "/api/v1/expenses/discards", "discarded", ids);
    }

    private void resolve(String stage, String path, String countField, List<Long> ids) {
        JsonNode body =
                send(stage, "POST", path, JSON.writeValueAsString(Map.of("ids", ids)), "application/json", Set.of(200));
        if (body.path(countField).asInt() != ids.size()) {
            throw new EvalException(stage, path + " resolved " + body + " of " + ids.size() + " ids");
        }
    }

    private JsonNode send(
            String stage, String method, String path, String body, String contentType, Set<Integer> expected) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .method(
                        method,
                        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        if (!cookies.isEmpty()) {
            request.header(
                    "Cookie",
                    cookies.entrySet().stream()
                            .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                            .collect(Collectors.joining("; ")));
        }
        if (!method.equals("GET") && cookies.containsKey("XSRF-TOKEN")) {
            request.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
        }

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new EvalException(stage, method + " " + path + " failed: " + e.getMessage(), null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EvalException(stage, method + " " + path + " was interrupted", null, e);
        }

        response.headers().allValues("Set-Cookie").forEach(this::keepCookie);
        if (!expected.contains(response.statusCode())) {
            throw new EvalException(stage, method + " " + path + " answered " + response.body(), response.statusCode());
        }
        return response.body().isBlank() ? JSON.missingNode() : JSON.readTree(response.body());
    }

    private void keepCookie(String setCookie) {
        String pair = setCookie.split(";", 2)[0];
        int equals = pair.indexOf('=');
        if (equals > 0) {
            cookies.put(
                    pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
        }
    }
}
