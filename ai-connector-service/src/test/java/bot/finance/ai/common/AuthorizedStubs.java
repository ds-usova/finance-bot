package bot.finance.ai.common;

import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

/**
 * Attaches an {@code authorization} header to a generated stub, for the tests that enter
 * {@code IntentExtractionService} — which refuses a call carrying none.
 */
public final class AuthorizedStubs {

    public static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private AuthorizedStubs() {}

    public static <S extends AbstractStub<S>> S withCallerToken(S stub, String token) {
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, token);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }
}
