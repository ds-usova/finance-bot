package bot.finance.adapter.aiconnector;

import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.health.v1.HealthGrpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class AiConnectorChannelConfiguration {

    @Bean
    ManagedChannel aiConnectorChannel(GrpcChannelFactory grpcChannelFactory) {
        return grpcChannelFactory.createChannel("ai-connector");
    }

    @Bean
    IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub intentExtractionServiceBlockingStub(
            ManagedChannel aiConnectorChannel) {
        return IntentExtractionServiceGrpc.newBlockingStub(aiConnectorChannel);
    }

    @Bean
    HealthGrpc.HealthBlockingStub aiConnectorHealthBlockingStub(ManagedChannel aiConnectorChannel) {
        return HealthGrpc.newBlockingStub(aiConnectorChannel);
    }
}
