package bot.finance.ai.adapter.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import bot.finance.ai.adapter.grpc.CallerTokenTestSupport;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CallerTokenLedgerTurnInterceptorTest {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final MethodDescriptor.Marshaller<String> MARSHALLER = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            throw new UnsupportedOperationException();
        }
    };

    private static final MethodDescriptor<String, String> METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("ledger.LedgerTurnService/Turn")
            .setRequestMarshaller(MARSHALLER)
            .setResponseMarshaller(MARSHALLER)
            .build();

    private final CallerTokenLedgerTurnInterceptor interceptor = new CallerTokenLedgerTurnInterceptor();

    @SuppressWarnings("unchecked")
    private final ClientCall<String, String> delegateCall = mock(ClientCall.class);

    private final Channel next = mock(Channel.class);

    private static class RecordingListener extends ClientCall.Listener<String> {
        final AtomicReference<Status> closedWith = new AtomicReference<>();

        @Override
        public void onClose(Status status, Metadata trailers) {
            closedWith.set(status);
        }
    }

    @Nested
    @DisplayName("interceptCall()")
    class InterceptCall {

        @Test
        @DisplayName("when the turn holds a caller token - then the call's headers carry it as a bearer authorization")
        void whenTurnHoldsCallerToken_thenHeadersCarryItAsBearerAuthorization() {
            doReturn(delegateCall).when(next).newCall(any(), any());
            Metadata headers = new Metadata();

            CallerTokenTestSupport.withCallerToken("caller-token-1", () -> {
                ClientCall<String, String> call = interceptor.interceptCall(METHOD, CallOptions.DEFAULT, next);
                call.start(new RecordingListener(), headers);
            });

            assertThat(headers.get(AUTHORIZATION)).isEqualTo("Bearer caller-token-1");
        }

        @Test
        @DisplayName("when the turn holds no caller token - then the call fails and the next channel never sees it")
        void whenTurnHoldsNoCallerToken_thenCallFailsAndNextChannelNeverSeesIt() {
            doReturn(delegateCall).when(next).newCall(any(), any());
            RecordingListener listener = new RecordingListener();
            Metadata headers = new Metadata();

            Throwable thrown = null;
            try {
                ClientCall<String, String> call = interceptor.interceptCall(METHOD, CallOptions.DEFAULT, next);
                call.start(listener, headers);
            } catch (RuntimeException e) {
                thrown = e;
            }

            Status closed = listener.closedWith.get();
            assertThat(thrown != null || (closed != null && !closed.isOk()))
                    .as("the call failed before it was sent")
                    .isTrue();
            assertThat(headers.get(AUTHORIZATION)).isNull();
            verify(next, never()).newCall(any(), any());
            verify(delegateCall, never()).start(any(), any());
            verify(delegateCall, never()).sendMessage(any());
        }
    }
}
