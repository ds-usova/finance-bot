package bot.finance.ai.common;

import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full application over the in-process gRPC transport, for the inbound-adapter test. Isolation comes
 * from {@code @MockitoBean} on {@code ExtractIntentsPort} in the test class, not from a framework slice. Every
 * test autowires {@link IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub} rather than building
 * its own channel.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub.class)
public @interface GrpcAdapterTest {}
