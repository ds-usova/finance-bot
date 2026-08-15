package bot.finance.common.fixtures;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;

/**
 * The JSON Patch bodies a browser sends to {@code /api/v1/expenses}, carrying the session cookie and CSRF token
 * a write needs.
 */
public class ExpensePatches {

    private static final String EXPENSES_PATH = "/api/v1/expenses";
    private static final String PATCH_MEDIA_TYPE = "application/json-patch+json";

    private ExpensePatches() {}

    /** Refiles a recorded expense under another category, the way the browser's category picker does. */
    public static Response replaceCategory(String sessionCookie, String csrfToken, long expenseId, long categoryId) {
        return RestAssured.given()
                .contentType(PATCH_MEDIA_TYPE)
                .cookie(BrowserSessions.COOKIE_NAME, sessionCookie)
                .cookie(BrowserSessions.CSRF_COOKIE, csrfToken)
                .header(BrowserSessions.CSRF_HEADER, csrfToken)
                .body(List.of(Map.of("op", "replace", "path", "/categoryId", "value", categoryId)))
                .when()
                .patch("%s/RECORDED/%d".formatted(EXPENSES_PATH, expenseId));
    }
}
