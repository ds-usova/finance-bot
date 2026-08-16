package bot.finance.ai.common.boot;

import io.grpc.ChannelCredentials;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.util.Collections;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.client.ClientInterceptorsConfigurer;
import org.springframework.grpc.client.DefaultGrpcChannelFactory;
import org.springframework.grpc.server.DefaultGrpcServerFactory;
import org.springframework.grpc.server.ServerServiceDefinitionFilter;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;
import org.springframework.grpc.server.service.GrpcServiceConfigurer;
import org.springframework.grpc.server.service.GrpcServiceDiscoverer;

/**
 * Mirrors Boot's {@code TestGrpcTransportAutoConfiguration}, except the in-process name is generated per
 * configuration instance rather than held in a {@code static} field. Boot's version shares one name across the
 * whole JVM, so a second {@code @GrpcAdapterTest} context — one holding a nested {@code @MockitoBean} group —
 * fails to start with "name already registered" while the first context is still cached and running.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InProcessGrpcTransportConfiguration {

    private final String address = InProcessServerBuilder.generateName();

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    InProcessTestGrpcServerFactory inProcessTestGrpcServerFactory(
            GrpcServiceDiscoverer serviceDiscoverer,
            GrpcServiceConfigurer serviceConfigurer,
            ObjectProvider<ServerServiceDefinitionFilter> serviceFilter) {
        InProcessTestGrpcServerFactory factory = new InProcessTestGrpcServerFactory(address);
        serviceFilter.ifAvailable(factory::setServiceFilter);
        serviceDiscoverer.findServices().stream()
                .map(spec -> serviceConfigurer.configure(spec, factory))
                .forEach(factory::addService);
        return factory;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    GrpcServerLifecycle inProcessTestGrpcServerLifecycle(
            InProcessTestGrpcServerFactory serverFactory,
            GrpcServerProperties properties,
            ApplicationEventPublisher eventPublisher) {
        return new GrpcServerLifecycle(serverFactory, properties.getShutdown().getGracePeriod(), eventPublisher);
    }

    @Bean
    ClientInterceptorsConfigurer inProcessTestClientInterceptorsConfigurer(ApplicationContext applicationContext) {
        return new ClientInterceptorsConfigurer(applicationContext);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    InProcessTestGrpcChannelFactory inProcessTestGrpcChannelFactory(
            ClientInterceptorsConfigurer interceptorsConfigurer) {
        InProcessTestGrpcChannelFactory factory = new InProcessTestGrpcChannelFactory(interceptorsConfigurer);
        factory.setVirtualTargets(target -> address);
        return factory;
    }

    private static final class InProcessTestGrpcServerFactory extends DefaultGrpcServerFactory<InProcessServerBuilder> {

        InProcessTestGrpcServerFactory(String address) {
            super(address, Collections.emptyList());
        }

        @Override
        protected InProcessServerBuilder newServerBuilder() {
            return InProcessServerBuilder.forName(address());
        }
    }

    private static final class InProcessTestGrpcChannelFactory
            extends DefaultGrpcChannelFactory<InProcessChannelBuilder> {

        InProcessTestGrpcChannelFactory(ClientInterceptorsConfigurer interceptorsConfigurer) {
            super(Collections.emptyList(), interceptorsConfigurer);
        }

        @Override
        public boolean supports(String target) {
            return true;
        }

        @Override
        protected InProcessChannelBuilder newChannelBuilder(String target, ChannelCredentials credentials) {
            return InProcessChannelBuilder.forName(target);
        }
    }
}
