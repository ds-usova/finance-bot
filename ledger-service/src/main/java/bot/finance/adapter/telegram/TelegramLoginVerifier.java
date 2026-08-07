package bot.finance.adapter.telegram;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Checks that a Login Widget payload really came from Telegram: the widget signs the fields it hands the browser
 * with a key derived from the bot token, so only the holder of that token can produce an accepted payload.
 */
@Component
public class TelegramLoginVerifier {

    private static final String ID_FIELD = "id";
    private static final String HASH_FIELD = "hash";
    private static final String AUTH_DATE_FIELD = "auth_date";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secretKey;
    private final Duration maxAge;

    public TelegramLoginVerifier(TelegramBotProperties botProperties, TelegramLoginProperties loginProperties) {
        this.secretKey = deriveSecretKey(botProperties.token());
        this.maxAge = loginProperties.maxAge();
    }

    /** @return the Telegram user id the payload is signed for, as the external id the ledger stores. */
    public String verify(Map<String, String> fields, Instant now) {
        String hash = fields.get(HASH_FIELD);
        if (hash == null || hash.isBlank()) {
            throw new TelegramLoginRejectedException("the sign-in payload carries no hash");
        }

        byte[] expected = sign(dataCheckString(fields));
        if (!MessageDigest.isEqual(expected, decodeHex(hash))) {
            throw new TelegramLoginRejectedException("the sign-in payload's hash does not match its fields");
        }
        checkFreshness(fields.get(AUTH_DATE_FIELD), now);

        String externalId = fields.get(ID_FIELD);
        if (externalId == null || externalId.isBlank()) {
            throw new TelegramLoginRejectedException("the sign-in payload carries no user id");
        }
        return externalId;
    }

    private void checkFreshness(String authDate, Instant now) {
        if (authDate == null || authDate.isBlank()) {
            throw new TelegramLoginRejectedException("the sign-in payload carries no auth_date");
        }

        Instant signedAt;
        try {
            signedAt = Instant.ofEpochSecond(Long.parseLong(authDate.trim()));
        } catch (NumberFormatException e) {
            throw new TelegramLoginRejectedException("the sign-in payload's auth_date is not epoch seconds");
        }

        if (signedAt.isAfter(now)) {
            throw new TelegramLoginRejectedException("the sign-in payload is dated in the future");
        }

        if (Duration.between(signedAt, now).compareTo(maxAge) > 0) {
            throw new TelegramLoginRejectedException("the sign-in payload is older than telegram.login.max-age");
        }
    }

    /**
     * Every field received except {@code hash}, sorted by key, rendered {@code key=value} and joined by newlines.
     * A field Telegram adds later is signed by the widget and so has to be included, even though nothing here
     * knows what it means.
     */
    private static String dataCheckString(Map<String, String> fields) {
        Map<String, String> signed = new TreeMap<>(fields);
        signed.remove(HASH_FIELD);
        return signed.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private byte[] sign(String dataCheckString) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGORITHM));
            return mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("failed to sign the Telegram sign-in check string", e);
        }
    }

    private static byte[] decodeHex(String hash) {
        try {
            return HexFormat.of().parseHex(hash.trim());
        } catch (IllegalArgumentException e) {
            throw new TelegramLoginRejectedException("the sign-in payload's hash is not hexadecimal");
        }
    }

    private static byte[] deriveSecretKey(String botToken) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(botToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
