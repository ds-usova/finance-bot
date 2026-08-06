package bot.finance.adapter.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("web.session")
public record WebSessionProperties(String cookieName, boolean secure, String sameSite, String path) {}
