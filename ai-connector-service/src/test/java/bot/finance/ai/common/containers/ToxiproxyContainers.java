package bot.finance.ai.common.containers;

import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Fronts {@link RedisContainers#REDIS_CONTAINER} on the shared {@link Network}, so an outage-and-recovery capture
 * test can cut and restore the connection inside one booted context. A static URL cannot express that: the test
 * points its own context's {@code spring.data.redis.url} at {@link #proxiedRedisUrl()} and toggles the toxic on
 * {@link #REDIS_PROXY}, never stopping either singleton.
 */
public class ToxiproxyContainers {

    private static final DockerImageName TOXIPROXY_IMAGE = DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.9.0");
    private static final int REDIS_PORT = 6379;

    public static final ToxiproxyContainer TOXIPROXY_CONTAINER;
    public static final ToxiproxyContainer.ContainerProxy REDIS_PROXY;

    static {
        TOXIPROXY_CONTAINER = new ToxiproxyContainer(TOXIPROXY_IMAGE).withNetwork(Network.NETWORK);
        TOXIPROXY_CONTAINER.start();

        REDIS_PROXY = TOXIPROXY_CONTAINER.getProxy(RedisContainers.REDIS_CONTAINER, REDIS_PORT);
    }

    private ToxiproxyContainers() {}

    public static String proxiedRedisUrl() {
        return "redis://" + REDIS_PROXY.getContainerIpAddress() + ":" + REDIS_PROXY.getProxyPort();
    }
}
