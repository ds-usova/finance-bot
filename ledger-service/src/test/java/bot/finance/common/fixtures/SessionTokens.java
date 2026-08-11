package bot.finance.common.fixtures;

import bot.finance.adapter.security.SessionTokenMinter;
import bot.finance.adapter.security.SessionTokenProperties;
import java.time.Duration;

/** The {@code session.token.*} configuration the test profile runs with, for a hand-built minter. */
public final class SessionTokens {

    public static final String ISSUER = "ledger-service";
    public static final String AUDIENCE = "web-app";
    public static final Duration TTL = Duration.ofDays(7);

    private SessionTokens() {}

    public static SessionTokenProperties properties() {
        return new SessionTokenProperties(ISSUER, AUDIENCE, TTL);
    }

    public static SessionTokenMinter minter() {
        return new SessionTokenMinter(properties(), SigningKeys.keys());
    }

    public static String tokenFor(long userId) {
        return minter().mint(userId);
    }
}
