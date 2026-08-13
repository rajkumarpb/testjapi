package javapi.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ServiceLoader;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcPoolFactoryTest {

    private static final String URL = "jdbc:h2:mem:pooltest;DB_CLOSE_DELAY=-1";

    @Test
    void registeredAsServiceLoaderProvider() {
        boolean found = false;
        for (DataSourceFactory factory : ServiceLoader.load(DataSourceFactory.class)) {
            found = true;
            assertTrue(factory instanceof JdbcPoolFactory, "expected JdbcPoolFactory, got " + factory.getClass());
        }
        assertTrue(found, "no DataSourceFactory registered");
    }

    @Test
    void roundTripsRowsOverPooledConnections() throws Exception {
        try (HikariDataSource pool = (HikariDataSource) new JdbcPoolFactory().create(URL, "sa", "")) {
            DataSource dataSource = pool;
            try (Connection first = dataSource.getConnection()) {
                assertNotNull(first);
                try (Statement statement = first.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS pool_items (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100))");
                    statement.execute("INSERT INTO pool_items(name) VALUES ('alpha')");
                }
            }
            try (Connection second = dataSource.getConnection()) {
                try (Statement statement = second.createStatement();
                        ResultSet result = statement.executeQuery("SELECT name FROM pool_items")) {
                    assertTrue(result.next(), "row should be visible on a second pooled connection");
                    assertEquals("alpha", result.getString(1));
                    assertFalse(result.next());
                }
            }
        }
    }
}
