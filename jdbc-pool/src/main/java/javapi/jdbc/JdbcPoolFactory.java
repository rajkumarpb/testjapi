package javapi.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import javapi.config.Config;

/**
 * HikariCP-backed {@link DataSourceFactory}, picked up automatically via
 * {@code ServiceLoader} when {@code jdbc-pool} is on the classpath.
 *
 * <p>Pool settings come from the {@code db.pool.*} config keys (defaults in
 * parentheses): {@code db.pool.max} (10), {@code db.pool.min} (2),
 * {@code db.pool.name} (javapi-pool), {@code db.pool.timeout} (30000 ms).</p>
 */
public final class JdbcPoolFactory implements DataSourceFactory {

    private static final int DEFAULT_MAX = 10;
    private static final int DEFAULT_MIN = 2;
    private static final long DEFAULT_TIMEOUT = 30_000;

    @Override
    public DataSource create(String url, String user, String password) {
        Config config = Config.load();
        int maximumPoolSize = config.getInt("db.pool.max", DEFAULT_MAX);
        int minimumIdle = Math.min(config.getInt("db.pool.min", DEFAULT_MIN), maximumPoolSize);
        long connectionTimeout = config.getInt("db.pool.timeout", (int) DEFAULT_TIMEOUT);

        HikariConfig pool = new HikariConfig();
        pool.setPoolName(config.get("db.pool.name", "javapi-pool"));
        pool.setJdbcUrl(url);
        if (user != null && !user.isBlank()) {
            pool.setUsername(user);
        }
        if (password != null) {
            pool.setPassword(password);
        }
        pool.setMaximumPoolSize(maximumPoolSize);
        pool.setMinimumIdle(minimumIdle);
        pool.setConnectionTimeout(connectionTimeout);
        return new HikariDataSource(pool);
    }
}
