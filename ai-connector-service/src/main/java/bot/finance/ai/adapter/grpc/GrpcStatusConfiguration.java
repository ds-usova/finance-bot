package bot.finance.ai.adapter.grpc;

import bot.finance.ai.domain.exception.IntentInferenceException;
import io.grpc.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

@Configuration
public class GrpcStatusConfiguration {

    @Bean
    GrpcExceptionHandler grpcExceptionHandler() {
        return throwable -> {
            if (throwable instanceof IntentInferenceException) {
                return Status.UNAVAILABLE.withDescription(throwable.getMessage()).withCause(throwable).asException();
            }
            return null;
        };
    }

}
