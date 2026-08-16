package bot.finance.ai.common.containers;

import java.net.URI;
import java.time.Duration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class RedisContainers {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:8");
    private static final int REDIS_PORT = 6379;
    private static final int CLOSED_PORT = 1;
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(2);

    public static final GenericContainer<?> REDIS_CONTAINER;

    static {
        REDIS_CONTAINER = new GenericContainer<>(REDIS_IMAGE)
                .withNetwork(Network.NETWORK)
                .withNetworkAliases("connector_redis")
                .withExposedPorts(REDIS_PORT)
                .waitingFor(Wait.forListeningPort());

        REDIS_CONTAINER.start();
    }

    private RedisContainers() {}

    /**
     * Where the singleton itself is reachable, for a test wiring {@code spring.data.redis.url} at a working
     * Redis. A test that needs Redis to refuse points its own context at a closed port instead, and never stops
     * this container.
     */
    public static String redisUrl() {
        return "redis://" + REDIS_CONTAINER.getHost() + ":" + REDIS_CONTAINER.getMappedPort(REDIS_PORT);
    }

    /**
     * A started factory at the singleton, for a test building a Redis adapter itself rather than autowiring one.
     * The caller destroys it.
     */
    public static LettuceConnectionFactory connectionFactory() {
        return connectionFactoryFor(redisUrl());
    }

    /**
     * A started factory at whatever {@code redis://host:port} names — the singleton itself, or
     * {@link ToxiproxyContainers#proxiedRedisUrl()} for a test that cuts the connection. The caller destroys it.
     */
    public static LettuceConnectionFactory connectionFactoryFor(String redisUrl) {
        URI uri = URI.create(redisUrl);
        return connectionFactoryAt(uri.getHost(), uri.getPort());
    }

    /**
     * A started factory at a port nothing listens on, for a test asserting what an adapter does during an outage.
     * The singleton is left running: stopping it would take every other test's Redis with it.
     */
    public static LettuceConnectionFactory unreachableConnectionFactory() {
        return connectionFactoryAt("localhost", CLOSED_PORT);
    }

    /** A template over one of the factories above, ready to use. */
    public static StringRedisTemplate template(LettuceConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    private static LettuceConnectionFactory connectionFactoryAt(String host, int port) {
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(COMMAND_TIMEOUT)
                .build();
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfiguration);
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }
}
