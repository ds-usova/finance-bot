package bot.finance.ai.adapter.ledger;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledger.mcp")
public record LedgerMcpProperties(String url) {

}
