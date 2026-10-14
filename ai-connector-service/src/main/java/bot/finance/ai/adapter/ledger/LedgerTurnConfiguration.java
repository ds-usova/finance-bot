package bot.finance.ai.adapter.ledger;

import bot.finance.ledger.adapter.grpc.v1.LedgerTurnServiceGrpc;
import io.grpc.Channel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
@ConditionalOnProperty(name = "extraction.mode", havingValue = "classifier")
@EnableConfigurationProperties(LedgerTurnProperties.class)
public class LedgerTurnConfiguration {

    private static final String LEDGER_TURN_CHANNEL = "ledger-turn";

    @Bean
    LedgerTurnServiceGrpc.LedgerTurnServiceBlockingStub ledgerTurnStub(GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.createChannel(LEDGER_TURN_CHANNEL);
        return LedgerTurnServiceGrpc.newBlockingStub(channel).withInterceptors(new CallerTokenLedgerTurnInterceptor());
    }
}
