package bot.finance.common.containers;

public class Network {

    public static final org.testcontainers.containers.Network NETWORK =
            org.testcontainers.containers.Network.newNetwork();

    private Network() {}
}
