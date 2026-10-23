package bot.finance.eval.ledger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The cookies the ledger has set, kept by hand: the session cookie may be Secure while the ledger is reached over
 * plain http, and a standard cookie handler would then never send it.
 */
final class CookieJar {

    private final Map<String, String> cookies = new LinkedHashMap<>();

    void keep(List<String> setCookieHeaders) {
        for (String setCookie : setCookieHeaders) {
            String pair = setCookie.split(";", 2)[0];
            int equals = pair.indexOf('=');
            if (equals > 0) {
                cookies.put(
                        pair.substring(0, equals).trim(),
                        pair.substring(equals + 1).trim());
            }
        }
    }

    Optional<String> get(String name) {
        return Optional.ofNullable(cookies.get(name));
    }

    /** The {@code Cookie} header value, or empty when nothing has been set. */
    Optional<String> header() {
        return cookies.isEmpty()
                ? Optional.empty()
                : Optional.of(cookies.entrySet().stream()
                        .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                        .collect(Collectors.joining("; ")));
    }
}
