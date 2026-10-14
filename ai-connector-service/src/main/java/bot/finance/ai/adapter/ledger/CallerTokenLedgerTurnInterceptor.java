package bot.finance.ai.adapter.ledger;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;

public class CallerTokenLedgerTurnInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        // stub-intent: attaches the turn's caller token as `authorization: Bearer <token>` metadata, read from
        // CallerTokenContext; a call made with no token held is failed before it is sent
        return next.newCall(method, callOptions);
    }
}
