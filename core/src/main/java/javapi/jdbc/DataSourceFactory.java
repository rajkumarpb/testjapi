package javapi.jdbc;

import javax.sql.DataSource;

public interface DataSourceFactory {

    DataSource create(String url, String user, String password);
}
