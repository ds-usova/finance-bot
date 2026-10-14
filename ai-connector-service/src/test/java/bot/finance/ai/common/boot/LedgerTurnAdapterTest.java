package bot.finance.ai.common.boot;

import bot.finance.ai.adapter.ledger.GrpcLedgerTurnAdapter;
import bot.finance.ai.adapter.ledger.LedgerTurnConfiguration;
import bot.finance.ai.adapter.logging.Slf4jLoggerFactory;
import bot.finance.ai.common.containers.LedgerTurnServer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.grpc.client.autoconfigure.CompositeChannelFactoryAutoConfiguration;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Boots {@link GrpcLedgerTurnAdapter} and {@link LedgerTurnConfiguration} with Spring gRPC's client
 * autoconfiguration, {@code extraction.mode=classifier}, {@code ledger.turn.target} at {@link LedgerTurnServer}'s
 * port and a one-second timeout — no gRPC server, no other adapter.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@SpringBootTest(
        classes = {GrpcLedgerTurnAdapter.class, LedgerTurnConfiguration.class, Slf4jLoggerFactory.class},
        properties = {"extraction.mode=classifier", "ledger.turn.timeout=1s"})
@ImportAutoConfiguration({
    SslAutoConfiguration.class,
    CompositeChannelFactoryAutoConfiguration.class,
    GrpcClientAutoConfiguration.class
})
@Import(LedgerTurnAdapterTest.LedgerTurnTargetConfiguration.class)
public @interface LedgerTurnAdapterTest {

    /**
     * Points {@code ledger.turn.target} at {@link LedgerTurnServer}'s port, which is only known at runtime and so
     * cannot live in {@code application-test.yaml}.
     */
    @TestConfiguration(proxyBeanMethods = false)
    class LedgerTurnTargetConfiguration {

        @Bean
        DynamicPropertyRegistrar ledgerTurnTarget() {
            return registry -> registry.add("ledger.turn.target", LedgerTurnServer::target);
        }
    }
}
