package javapi.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ServiceLoader;
import javax.sql.DataSource;
import javapi.di.DI;
import javapi.request.HttpException;

public final class JdbcSetup {

    private JdbcSetup() {
    }

    public static void register(DI di, String url, String user, String password) {
        DataSource dataSource = create(url, user, password);
        di.component(DataSource.class, dataSource);
        di.requestScoped(Connection.class, ctx -> open(dataSource));
        di.requestScoped(Jdbc.class, ctx -> new Jdbc((Connection) ctx.resolve(Connection.class)));
    }

    private static DataSource create(String url, String user, String password) {
        for (DataSourceFactory factory : ServiceLoader.load(DataSourceFactory.class)) {
            return factory.create(url, user, password);
        }
        return new JdbcDataSource(url, user, password);
    }

    private static Connection open(DataSource dataSource) {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            e.printStackTrace(System.err);
            throw new HttpException(503, "Database unavailable");
        }
    }
}
