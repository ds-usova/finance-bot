package bot.finance.adapter.logging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TurnCallLogProperties.class)
public class LoggingConfiguration {}
