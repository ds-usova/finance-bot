package bot.finance.ai.common.boot;

import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots the full application over the in-process gRPC transport, for the inbound-adapter test. Isolation comes
 * from {@code @MockitoBean} on {@code ExtractIntentsPort} in the test class, not from a framework slice. Every
 * test autowires {@link IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub} rather than building
 * its own channel. {@link InProcessGrpcTransportConfiguration} stands in for Boot's
 * {@code @AutoConfigureTestGrpcTransport}, which shares one in-process server name across the whole JVM and so
 * cannot serve two contexts — one per {@code @MockitoBean} group — at once; the properties below are the ones
 * that annotation would otherwise have mapped.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.grpc.server.servlet.enabled=false",
            "spring.grpc.server.factory.enabled=false",
            "spring.grpc.client.channelfactory.enabled=false"
        })
@Import(InProcessGrpcTransportConfiguration.class)
@ImportGrpcClients(types = IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub.class)
public @interface GrpcAdapterTest {}
