package bot.finance.common.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class RedisContainers {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:8");
    private static final int REDIS_PORT = 6379;

    public static final GenericContainer<?> REDIS_CONTAINER;

    static {
        REDIS_CONTAINER = new GenericContainer<>(REDIS_IMAGE)
                .withNetwork(Network.NETWORK)
                .withNetworkAliases("ledger_redis")
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
}
