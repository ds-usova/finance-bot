package bot.finance.ai.adapter.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.springframework.stereotype.Component;

@Component
public class CallerTokenInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        // TODO: read `authorization` off `headers` into the CallerTokenUtils.CALLER_TOKEN context key for the
        // call's duration. Scope the UNAUTHENTICATED refusal to IntentExtractionService — grpc.health.v1.Health
        // stays open, since the ledger's AiConnectorHealthIndicator probes it with no token.
        Context context = Context.current();
        return Contexts.interceptCall(context, call, headers, next);
    }
}
