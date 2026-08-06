package bot.finance.adapter.web;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.adapter.security.SessionTokenMinter;
import bot.finance.adapter.security.SessionTokenProperties;
import bot.finance.adapter.security.WebSessionProperties;
import bot.finance.adapter.telegram.TelegramLoginVerifier;
import bot.finance.application.dto.InitializeUserCommand;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final TelegramLoginVerifier loginVerifier;
    private final InitializeUserPort initializeUserPort;
    private final SessionTokenMinter sessionTokenMinter;
    private final WebSessionProperties cookieProperties;
    private final Duration sessionTtl;
    private final Logger logger;

    public SessionController(
            TelegramLoginVerifier loginVerifier,
            InitializeUserPort initializeUserPort,
            SessionTokenMinter sessionTokenMinter,
            WebSessionProperties cookieProperties,
            SessionTokenProperties sessionTokenProperties,
            LoggerFactory loggerFactory) {
        this.loginVerifier = loginVerifier;
        this.initializeUserPort = initializeUserPort;
        this.sessionTokenMinter = sessionTokenMinter;
        this.cookieProperties = cookieProperties;
        this.sessionTtl = sessionTokenProperties.ttl();
        this.logger = loggerFactory.getLogger(SessionController.class);
    }

    @PostMapping
    public ResponseEntity<SessionResponse> signIn(@RequestBody Map<String, String> telegramLoginPayload) {
        String externalId = loginVerifier.verify(telegramLoginPayload, Instant.now());

        initializeUserPort.initialize(new InitializeUserCommand(externalId));
        String token = sessionTokenMinter.mint(externalId);
        logger.info("opened a browser session for user {}", externalId);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(token, sessionTtl))
                .body(new SessionResponse(externalId));
    }

    @GetMapping
    public SessionResponse currentSession() {
        return new SessionResponse(AuthenticatedCaller.authenticatedUserId().externalId());
    }

    @DeleteMapping
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
