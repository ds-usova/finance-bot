package bot.finance.ai.adapter.ledger;

import bot.finance.ai.adapter.grpc.CallerTokenContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

public class CallerTokenLedgerTurnInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return CallerTokenContext.callerToken()
                .<ClientCall<ReqT, RespT>>map(token ->
                        new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
                            @Override
                            public void start(Listener<RespT> responseListener, Metadata headers) {
                                headers.put(AUTHORIZATION, token);
                                super.start(responseListener, headers);
                            }
                        })
                .orElseGet(CallerTokenLedgerTurnInterceptor::unauthenticatedCall);
    }

    private static <ReqT, RespT> ClientCall<ReqT, RespT> unauthenticatedCall() {
        return new ClientCall<>() {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                responseListener.onClose(
                        Status.UNAUTHENTICATED.withDescription("No caller token held for the ledger turn call"),
                        new Metadata());
            }

            @Override
            public void request(int numMessages) {}

            @Override
            public void cancel(String message, Throwable cause) {}

            @Override
            public void halfClose() {}

            @Override
            public void sendMessage(ReqT message) {}
        };
    }
}
