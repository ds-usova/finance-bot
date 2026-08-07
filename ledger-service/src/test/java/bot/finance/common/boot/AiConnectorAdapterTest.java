package bot.finance.common.boot;

import bot.finance.adapter.aiconnector.AiConnectorChannelConfiguration;
import bot.finance.adapter.aiconnector.AiConnectorHealthIndicator;
import bot.finance.adapter.aiconnector.AiConnectorIntentExtractionAdapter;
import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.adapter.security.AccessTokenProperties;
import bot.finance.adapter.security.TokenSigningKeys;
import bot.finance.adapter.security.TokenSigningProperties;
import bot.finance.common.containers.GrpcStubServer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.grpc.client.autoconfigure.CompositeChannelFactoryAutoConfiguration;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Boots {@link AiConnectorIntentExtractionAdapter}, {@link AiConnectorHealthIndicator} and
 * {@link AiConnectorChannelConfiguration} over a real gRPC client channel dialing
 * {@link GrpcStubServer} -- nothing is mocked. {@code spring.grpc.client.channel.ai-connector.target} is
 * redirected to the stub server's dynamic port through a {@link DynamicPropertyRegistrar} bean:
 * {@code @DynamicPropertySource} needs a static method inside a class body, which an annotation type
 * cannot declare, so this is the composed-annotation-compatible equivalent.
 *
 * <p>{@link SslAutoConfiguration} is listed because {@code GrpcClientAutoConfiguration}'s channel-credentials
 * bean requires {@code SslBundles}, which nothing else here contributes; without it the context fails to load
 * before any test body runs.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@SpringBootTest(
        classes = {
            AiConnectorIntentExtractionAdapter.class,
            AiConnectorHealthIndicator.class,
            AiConnectorChannelConfiguration.class,
            AccessTokenMinter.class,
            TokenSigningKeys.class,
            Slf4jLoggerFactory.class
        })
@EnableConfigurationProperties({AccessTokenProperties.class, TokenSigningProperties.class})
@ImportAutoConfiguration({
    SslAutoConfiguration.class,
    GrpcClientAutoConfiguration.class,
    CompositeChannelFactoryAutoConfiguration.class
})
@Import(AiConnectorAdapterTest.GrpcStubServerTargetConfiguration.class)
public @interface AiConnectorAdapterTest {

    @TestConfiguration(proxyBeanMethods = false)
    class GrpcStubServerTargetConfiguration {

        @Bean
        DynamicPropertyRegistrar aiConnectorTarget() {
            return registry -> registry.add("spring.grpc.client.channel.ai-connector.target", GrpcStubServer::target);
        }
    }
}
