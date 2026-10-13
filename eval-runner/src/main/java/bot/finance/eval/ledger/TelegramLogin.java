package bot.finance.eval.ledger;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signs a login payload the way the Telegram Login Widget does, with the bot token the ledger verifies against. */
public final class TelegramLogin {

    private TelegramLogin() {}

    public static Map<String, String> payload(long telegramId, long authDateEpochSeconds, String botToken) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", Long.toString(telegramId));
        fields.put("first_name", "Eval");
        fields.put("auth_date", Long.toString(authDateEpochSeconds));
        fields.put("hash", sign(fields, botToken));
        return fields;
    }

    static String sign(Map<String, String> fields, String botToken) {
        String checkString = new TreeMap<>(fields)
                .entrySet().stream()
                        .filter(entry -> !entry.getKey().equals("hash"))
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("\n"));
        try {
            byte[] secret = MessageDigest.getInstance("SHA-256").digest(botToken.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(checkString.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot sign the login payload", e);
        }
    }
}
