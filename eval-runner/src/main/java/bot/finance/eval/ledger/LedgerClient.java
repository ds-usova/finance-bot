package bot.finance.eval.ledger;

import bot.finance.eval.EvalException;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** One signed-in person's view of the ledger's REST API. */
public final class LedgerClient {

    private static final String JSON = "application/json";
    private static final String JSON_PATCH = "application/json-patch+json";
    private static final int PAGE_SIZE = 100;

    private final LedgerHttp http;

    public LedgerClient(HttpClient http, String baseUrl) {
        this.http = new LedgerHttp(http, baseUrl);
    }

    public void signIn(long telegramId, String botToken) {
        // An unauthenticated GET is answered 401 and still sets the CSRF cookie the sign-in needs.
        http.get("setup", "/api/v1/session", Set.of(200, 401));
        if (!http.hasCookie(LedgerHttp.CSRF_COOKIE)) {
            throw new EvalException("setup", "GET /api/v1/session set no " + LedgerHttp.CSRF_COOKIE + " cookie");
        }
        Map<String, String> payload =
                TelegramLogin.payload(telegramId, Instant.now().getEpochSecond(), botToken);
        http.send("setup", "POST", "/api/v1/session", payload, JSON, Set.of(200));
    }

    public void setDefaultCurrency(String currency) {
        http.send("setup", "PUT", "/api/v1/preferences", Map.of("defaultCurrency", currency), JSON, Set.of(200));
    }

    public long pendingTotal() {
        return http.get("setup", "/api/v1/expenses?status=PENDING&limit=1", Set.of(200))
                .path("total")
                .asLong();
    }

    public long fileMessage(String stage, String text) {
        return http.send(stage, "POST", "/api/v1/messages", Map.of("text", text), JSON, Set.of(202))
                .path("id")
                .asLong();
    }

    public WrittenMessage message(String stage, long id) {
        JsonNode body = http.get(stage, "/api/v1/messages/" + id, Set.of(200));
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
            JsonNode page = http.get(
                    stage, "/api/v1/expenses?status=PENDING&limit=" + PAGE_SIZE + "&offset=" + offset, Set.of(200));
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
        for (JsonNode node : http.get(stage, "/api/v1/categories", Set.of(200))) {
            categories.add(new Category(
                    node.path("id").asLong(),
                    node.path("name").asString(),
                    node.path("groupingId").asLong(),
                    node.path("groupingName").asString()));
        }
        return categories;
    }

    public void changeCategory(String stage, long expenseId, long categoryId) {
        List<Map<String, Object>> patch = List.of(Map.of("op", "replace", "path", "/categoryId", "value", categoryId));
        http.send(stage, "PATCH", "/api/v1/expenses/" + expenseId, patch, JSON_PATCH, Set.of(200));
    }

    public void accept(String stage, List<Long> ids) {
        resolve(stage, "/api/v1/expenses/acceptances", "accepted", ids);
    }

    public void discard(String stage, List<Long> ids) {
        resolve(stage, "/api/v1/expenses/discards", "discarded", ids);
    }

    private void resolve(String stage, String path, String countField, List<Long> ids) {
        JsonNode body = http.send(stage, "POST", path, Map.of("ids", ids), JSON, Set.of(200));
        if (body.path(countField).asInt() != ids.size()) {
            throw new EvalException(stage, path + " resolved " + body + " of " + ids.size() + " ids");
        }
    }
}
