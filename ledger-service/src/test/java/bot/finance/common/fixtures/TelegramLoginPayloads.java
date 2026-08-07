package bot.finance.common.fixtures;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Builds Login Widget payloads and signs them the way Telegram does, so a test can produce one the verifier
 * accepts without ever calling Telegram.
 */
public final class TelegramLoginPayloads {

    private TelegramLoginPayloads() {}

    /** A payload for {@code externalId}, dated now, signed with {@code botToken}. */
    public static Map<String, String> signedPayload(String botToken, String externalId) {
        return signedPayload(botToken, externalId, Instant.now());
    }

    public static Map<String, String> signedPayload(String botToken, String externalId, Instant authDate) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", externalId);
        fields.put("first_name", "Ada");
        fields.put("username", "ada");
        fields.put("auth_date", String.valueOf(authDate.getEpochSecond()));
        return sign(botToken, fields);
    }

    /** Adds {@code hash} over the fields given, leaving them otherwise untouched. */
    public static Map<String, String> sign(String botToken, Map<String, String> fields) {
        Map<String, String> signed = new LinkedHashMap<>(fields);
        signed.remove("hash");
        signed.put("hash", hash(botToken, signed));
        return signed;
    }

    private static String hash(String botToken, Map<String, String> fields) {
        Map<String, String> sorted = new TreeMap<>(fields);
        sorted.remove("hash");
        String dataCheckString = sorted.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        try {
            byte[] secretKey = MessageDigest.getInstance("SHA-256").digest(botToken.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("failed to sign a test Telegram sign-in payload", e);
        }
    }
}
