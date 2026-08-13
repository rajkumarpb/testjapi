package javapi.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javapi.request.HttpException;

public final class Jdbc {

    @FunctionalInterface
    public interface TxBlock<T> {
        T run() throws Exception;
    }

    private final Connection connection;

    public Jdbc(Connection connection) {
        this.connection = connection;
    }

    public Connection connection() {
        return connection;
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = new ArrayList<>();
        try (PreparedStatement statement = prepared(sql, params)) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(mapper.map(result));
                }
            }
        } catch (SQLException e) {
            throw toHttp(e);
        }
        return rows;
    }

    public <T> Optional<T> findOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public int update(String sql, Object... params) {
        try (PreparedStatement statement = prepared(sql, params)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw toHttp(e);
        }
    }

    public long insert(String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParams(statement, params);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new HttpException(500, "Insert did not return a generated key");
            }
        } catch (SQLException e) {
            throw toHttp(e);
        }
    }

    public <T> T tx(TxBlock<T> block) throws Exception {
        boolean wasAutoCommit = connection.getAutoCommit();
        if (wasAutoCommit) {
            connection.setAutoCommit(false);
        }
        try {
            T result = block.run();
            if (wasAutoCommit) {
                connection.commit();
            }
            return result;
        } catch (Exception e) {
            if (wasAutoCommit) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
            }
            throw e;
        } finally {
            if (wasAutoCommit) {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public static <T> T tx(Jdbc db, TxBlock<T> block) throws Exception {
        return db.tx(block);
    }

    private PreparedStatement prepared(String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        setParams(statement, params);
        return statement;
    }

    private static void setParams(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof Optional<?> optional) {
                statement.setObject(i + 1, optional.orElse(null));
            } else {
                statement.setObject(i + 1, params[i]);
            }
        }
    }

    private static HttpException toHttp(SQLException e) {
        e.printStackTrace(System.err);
        if (isConnectionError(e)) {
            return new HttpException(503, "Database unavailable");
        }
        return new HttpException(500, "Database error");
    }

    private static boolean isConnectionError(SQLException e) {
        for (SQLException current = e; current != null; current = current.getNextException()) {
            String state = current.getSQLState();
            if (state != null && state.startsWith("08")) {
                return true;
            }
        }
        return false;
    }
}
