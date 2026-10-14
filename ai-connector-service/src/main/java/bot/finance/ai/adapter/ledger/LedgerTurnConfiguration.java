package bot.finance.ai.adapter.ledger;

import bot.finance.ledger.adapter.grpc.v1.LedgerTurnEndServiceGrpc;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnServiceGrpc;
import io.grpc.Channel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
@EnableConfigurationProperties(LedgerTurnProperties.class)
public class LedgerTurnConfiguration {

    private static final String LEDGER_TURN_CHANNEL = "ledger-turn";

    @Bean
    Channel ledgerTurnChannel(GrpcChannelFactory channelFactory, LedgerTurnProperties properties) {
        return channelFactory.createChannel(LEDGER_TURN_CHANNEL);
    }

    @Bean
    LedgerTurnServiceGrpc.LedgerTurnServiceBlockingStub ledgerTurnStub(Channel ledgerTurnChannel) {
        return LedgerTurnServiceGrpc.newBlockingStub(ledgerTurnChannel)
                .withInterceptors(new CallerTokenLedgerTurnInterceptor());
    }

    @Bean
    LedgerTurnEndServiceGrpc.LedgerTurnEndServiceBlockingStub ledgerTurnEndStub(Channel ledgerTurnChannel) {
        return LedgerTurnEndServiceGrpc.newBlockingStub(ledgerTurnChannel)
                .withInterceptors(new CallerTokenLedgerTurnInterceptor());
    }
}
