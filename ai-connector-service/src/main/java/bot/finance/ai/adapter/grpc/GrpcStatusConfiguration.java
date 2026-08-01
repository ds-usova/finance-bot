package bot.finance.ai.adapter.grpc;

import bot.finance.ai.domain.exception.ExpenseProposalFailedException;
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
            if (throwable instanceof ExpenseProposalFailedException) {
                // TODO: FAILED_PRECONDITION when the ledger refused the proposal, UNAVAILABLE when it could
                // not be reached (D35), read off ExpenseProposalFailedException.reason().
                return Status.FAILED_PRECONDITION
                        .withDescription(throwable.getMessage())
                        .withCause(throwable)
                        .asException();
            }
            return null;
        };
    }

}
