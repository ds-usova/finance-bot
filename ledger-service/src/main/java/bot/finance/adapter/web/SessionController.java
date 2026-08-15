package bot.finance.adapter.web;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.adapter.security.SessionTokenMinter;
import bot.finance.adapter.security.SessionTokenProperties;
import bot.finance.adapter.security.WebSessionProperties;
import bot.finance.adapter.telegram.TelegramLoginVerifier;
import bot.finance.api.SessionApi;
import bot.finance.api.model.CurrentSession200Response;
import bot.finance.application.dto.InitializeUserCommand;
import bot.finance.application.dto.ReadSessionCommand;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.ReadSessionPort;
import bot.finance.domain.model.User;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController implements SessionApi {

    private final TelegramLoginVerifier loginVerifier;
    private final InitializeUserPort initializeUserPort;
    private final ReadSessionPort readSessionPort;
    private final SessionTokenMinter sessionTokenMinter;
    private final WebSessionProperties cookieProperties;
    private final Duration sessionTtl;
    private final Logger logger;

    public SessionController(
            TelegramLoginVerifier loginVerifier,
            InitializeUserPort initializeUserPort,
            ReadSessionPort readSessionPort,
            SessionTokenMinter sessionTokenMinter,
            WebSessionProperties cookieProperties,
            SessionTokenProperties sessionTokenProperties,
            LoggerFactory loggerFactory) {
        this.loginVerifier = loginVerifier;
        this.initializeUserPort = initializeUserPort;
        this.readSessionPort = readSessionPort;
        this.sessionTokenMinter = sessionTokenMinter;
        this.cookieProperties = cookieProperties;
        this.sessionTtl = sessionTokenProperties.ttl();
        this.logger = loggerFactory.getLogger(SessionController.class);
    }

    @Override
    public ResponseEntity<CurrentSession200Response> signIn(Map<String, Object> requestBody) {
        // The schema declares additionalProperties with no properties of its own, so the generated interface
        // types the body as a map of Object; each value is rendered with String.valueOf for the verifier.
        Map<String, String> telegramLoginPayload = new LinkedHashMap<>();
        requestBody.forEach((key, value) -> telegramLoginPayload.put(key, String.valueOf(value)));
        String externalId = loginVerifier.verify(telegramLoginPayload, Instant.now());

        User user = initializeUserPort.initialize(new InitializeUserCommand(externalId));
        long userId = user.id().orElseThrow();
        String token = sessionTokenMinter.mint(userId);
        logger.info("opened a browser session for user {}", userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(token, sessionTtl))
                .body(new CurrentSession200Response(externalId));
    }

    @Override
    public ResponseEntity<CurrentSession200Response> currentSession() {
        User user = readSessionPort.read(new ReadSessionCommand(AuthenticatedCaller.authenticatedUserId()));
        return ResponseEntity.ok(new CurrentSession200Response(user.externalId()));
    }

    @Override
    public ResponseEntity<Void> signOut() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO))
                .build();
    }

    private String sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(cookieProperties.cookieName(), value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.path())
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
