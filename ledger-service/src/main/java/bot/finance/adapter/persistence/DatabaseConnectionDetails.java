package bot.finance.adapter.persistence;

import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceUnwrapper;
import org.springframework.stereotype.Component;

/**
 * Where the pool is connected and as whom, for something that has to open its own connections rather than draw
 * them — an embedded replication engine, which is configured with a host, a port and a database name as well as
 * a URL.
 */
@Component
public class DatabaseConnectionDetails {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String host;
    private final int port;
    private final String databaseName;

    public DatabaseConnectionDetails(DataSource dataSource) {
        HikariDataSource hikariDataSource = hikariBehind(dataSource);
        this.jdbcUrl = hikariDataSource.getJdbcUrl();
        this.username = hikariDataSource.getUsername();
        this.password = hikariDataSource.getPassword();

        URI jdbcUri = URI.create(jdbcUrl.substring("jdbc:".length()));
        this.host = jdbcUri.getHost();
        this.port = jdbcUri.getPort();
        this.databaseName = jdbcUri.getPath().substring(1);
    }

    /**
     * The pool is routinely wrapped — lazily, for transactions, for tracing — so it is unwrapped rather than
     * cast. A datasource that is not Hikari underneath says so by name, since the alternative is a
     * {@link ClassCastException} naming nothing.
     */
    private static HikariDataSource hikariBehind(DataSource dataSource) {
        HikariDataSource hikariDataSource =
                DataSourceUnwrapper.unwrap(dataSource, HikariConfigMXBean.class, HikariDataSource.class);
        if (hikariDataSource == null) {
            throw new IllegalStateException("the datasource is not a Hikari pool but a "
                    + dataSource.getClass().getName());
        }
        return hikariDataSource;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String databaseName() {
        return databaseName;
    }
}
