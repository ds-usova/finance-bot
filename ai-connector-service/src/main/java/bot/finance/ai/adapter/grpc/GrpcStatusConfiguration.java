package bot.finance.ai.adapter.grpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

@Configuration
public class GrpcStatusConfiguration {

    @Bean
    GrpcExceptionHandler grpcExceptionHandler() {
        return throwable -> null;
    }
}
