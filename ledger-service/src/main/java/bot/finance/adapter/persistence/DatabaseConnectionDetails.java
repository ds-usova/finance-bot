package bot.finance.adapter.persistence;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Where the pool is connected and as whom, for something that has to open its own connection rather than draw one
 * — an embedded replication engine, which takes a host, a port and a database name rather than a JDBC URL.
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
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        this.jdbcUrl = hikariDataSource.getJdbcUrl();
        this.username = hikariDataSource.getUsername();
        this.password = hikariDataSource.getPassword();

        URI jdbcUri = URI.create(jdbcUrl.substring("jdbc:".length()));
        this.host = jdbcUri.getHost();
        this.port = jdbcUri.getPort();
        this.databaseName = jdbcUri.getPath().substring(1);
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
