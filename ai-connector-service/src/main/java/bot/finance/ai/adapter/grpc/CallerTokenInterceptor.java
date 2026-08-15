package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import bot.finance.ai.adapter.security.CallerTokenVerifier;
import bot.finance.ai.domain.exception.CallerNotIdentifiedException;
import bot.finance.ai.domain.exception.CallerVerificationUnavailableException;
import bot.finance.ai.domain.value.MessageIdentity;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Optional;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

@Component
@GlobalServerInterceptor
public class CallerTokenInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final Optional<CallerTokenVerifier> callerTokenVerifier;

    public CallerTokenInterceptor(Optional<CallerTokenVerifier> callerTokenVerifier) {
        this.callerTokenVerifier = callerTokenVerifier;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String token = headers.get(AUTHORIZATION);

        if (token == null && isIntentExtractionService(call)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing authorization header"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        Context context = Context.current().withValue(CallerTokenContext.CALLER_TOKEN, token);

        if (callerTokenVerifier.isPresent() && isIntentExtractionService(call)) {
            try {
                MessageIdentity identity = callerTokenVerifier.get().verify(token);
                context = context.withValue(CallerTokenContext.MESSAGE_IDENTITY, identity);
            } catch (CallerNotIdentifiedException e) {
                call.close(Status.UNAUTHENTICATED.withDescription(e.getMessage()), new Metadata());
                return new ServerCall.Listener<>() {};
            } catch (CallerVerificationUnavailableException e) {
                call.close(Status.UNAVAILABLE.withDescription(e.getMessage()), new Metadata());
                return new ServerCall.Listener<>() {};
            }
        }

        return Contexts.interceptCall(context, call, headers, next);
    }

    private boolean isIntentExtractionService(ServerCall<?, ?> call) {
        return IntentExtractionServiceGrpc.SERVICE_NAME.equals(
                call.getMethodDescriptor().getServiceName());
    }
}
