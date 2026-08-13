package javapi.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import javapi.di.DI;
import javapi.jdbcroutes.Item;
import javapi.jdbcroutes.ItemsController;
import javapi.request.HttpException;
import javapi.request.Request;
import javapi.routing.RouteScanner;
import javapi.routing.Router;

class JdbcTest {

    private static String url(String name) {
        return "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";
    }

    private static void schema(String url) throws Exception {
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            c.createStatement().execute("""
                    CREATE TABLE items (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      name VARCHAR(100) NOT NULL,
                      qty INT NOT NULL,
                      note VARCHAR(100),
                      status VARCHAR(20),
                      created DATE,
                      uid UUID
                    )""");
        }
    }

    private static Request request(String method, String path) {
        return Request.builder().method(method).path(path).build();
    }

    @Test
    void rowMapperMapsRecordWithOptionalEnumDateAndUuid() throws Exception {
        schema(url("rowmap"));
        UUID uid = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(url("rowmap"), "sa", "")) {
            c.createStatement().execute("INSERT INTO items(name, qty, note, status, created, uid) "
                    + "VALUES ('widget', 3, 'note', 'DONE', DATE '2026-08-01', '" + uid + "')");
            List<Item> rows = new Jdbc(c)
                    .query("SELECT * FROM items", RowMapper.from(Item.class));
            assertEquals(1, rows.size());
            Item item = rows.get(0);
            assertEquals("widget", item.name());
            assertEquals(3, item.qty());
            assertEquals(Optional.of("note"), item.note());
            assertEquals(Item.Status.DONE, item.status());
            assertEquals(LocalDate.of(2026, 8, 1), item.created());
            assertEquals(uid, item.uid());
        }
    }

    @Test
    void helperQueryFindOneUpdateInsertAndGeneratedKeys() throws Exception {
        schema(url("helper"));
        String dbUrl = url("helper");
        DI di = new DI();
        JdbcSetup.register(di, dbUrl, "sa", "");
        DI.Context ctx = di.open(request("GET", "/"));
        try {
            Jdbc db = (Jdbc) ctx.resolve(Jdbc.class);
            long id = db.insert("INSERT INTO items(name, qty) VALUES (?,?)", "alpha", 5);
            assertTrue(id >= 1);
            Optional<Item> found = db.findOne("SELECT * FROM items WHERE id = ?",
                    RowMapper.from(Item.class), id);
            assertTrue(found.isPresent());
            assertEquals("alpha", found.get().name());
            int updated = db.update("UPDATE items SET qty = ? WHERE id = ?", 9, id);
            assertEquals(1, updated);
            assertEquals(9, db.findOne("SELECT * FROM items WHERE id = ?",
                    RowMapper.from(Item.class), id).get().qty());
            assertTrue(db.findOne("SELECT * FROM items WHERE id = ?",
                    RowMapper.from(Item.class), 999).isEmpty());
        } finally {
            ctx.close();
        }
    }

    @Test
    void txCommitsOnSuccessAndRollsBackOnFailure() throws Exception {
        schema(url("tx"));
        try (Connection c = DriverManager.getConnection(url("tx"), "sa", "")) {
            Jdbc db = new Jdbc(c);
            db.tx(() -> {
                db.update("INSERT INTO items(name, qty) VALUES (?,?)", "kept", 1);
                return null;
            });
            assertThrows(IllegalStateException.class, () -> db.tx(() -> {
                db.update("INSERT INTO items(name, qty) VALUES (?,?)", "rolled-back", 2);
                throw new IllegalStateException("boom");
            }));
            List<Item> rows = db.query("SELECT * FROM items", RowMapper.from(Item.class));
            assertEquals(1, rows.size());
            assertEquals("kept", rows.get(0).name());
        }
    }

    @Test
    void requestScopedConnectionsAreNotSharedBetweenRequests() throws Exception {
        schema(url("scoped"));
        DI di = new DI();
        JdbcSetup.register(di, url("scoped"), "sa", "");
        DI.Context first = di.open(request("GET", "/"));
        DI.Context second = di.open(request("GET", "/"));
        try {
            Connection a = (Connection) first.resolve(Connection.class);
            Connection b = (Connection) second.resolve(Connection.class);
            assertNotSame(a, b);
            assertNotSame(first.resolve(Jdbc.class), second.resolve(Jdbc.class));
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void sqlErrorBecomesHttpException() throws Exception {
        schema(url("sqlerr"));
        try (Connection c = DriverManager.getConnection(url("sqlerr"), "sa", "")) {
            Jdbc db = new Jdbc(c);
            assertThrows(HttpException.class, () -> db.query("SELECT * FROM missing_table",
                    RowMapper.from(Item.class)));
        }
    }

    @Test
    void transactionAnnotationCommitsAndRollsBack() throws Exception {
        String dbUrl = url("txroute");
        schema(dbUrl);
        DI di = new DI();
        JdbcSetup.register(di, dbUrl, "sa", "");
        Router router = RouteScanner.scan(new Router(), "javapi.jdbcroutes", getClass().getClassLoader(), di);

        Request create = Request.builder().method("POST").path("/items").query("name=widget&qty=4").build();
        router.match(javapi.annotations.HttpMethod.POST, "/items").route().handler().handle(create);

        Object result = router.match(javapi.annotations.HttpMethod.GET, "/items/1")
                .route().handler().handle(Request.builder().method("GET").path("/items/1")
                        .pathParams(java.util.Map.of("id", "1")).build());
        assertEquals("widget", ((java.util.Optional<Item>) result).get().name());

        assertThrows(IllegalStateException.class,
                () -> router.match(javapi.annotations.HttpMethod.POST, "/items/fail")
                        .route().handler().handle(request("POST", "/items/fail")));

        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "")) {
            List<Item> rows = new Jdbc(c).query("SELECT * FROM items", RowMapper.from(Item.class));
            assertEquals(1, rows.size(), "rolled-back insert must not be committed");
        }
    }

    @Test
    void dependsDataSourceResolvesSingleton() throws Exception {
        String dbUrl = url("ds");
        schema(dbUrl);
        DI di = new DI();
        JdbcSetup.register(di, dbUrl, "sa", "");
        DI.Context ctx = di.open(request("GET", "/"));
        try {
            javax.sql.DataSource a = (javax.sql.DataSource) ctx.resolve(javax.sql.DataSource.class);
            javax.sql.DataSource b = (javax.sql.DataSource) ctx.resolve(javax.sql.DataSource.class);
            assertEquals(a, b);
            assertFalse(((Connection) ctx.resolve(Connection.class)).isClosed());
        } finally {
            ctx.close();
        }
    }
}
