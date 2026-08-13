package demo;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import javapi.core.JavAPI;
import javapi.middleware.Cors;

public class App {

    public static final String DEMO_DB_URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";

    public static void initSchema(String dbUrl) throws Exception {
        try (Connection connection = DriverManager.getConnection(dbUrl, "sa", "")) {
            connection.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS db_items (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      name VARCHAR(100) NOT NULL,
                      quantity INT NOT NULL,
                      supplier_email VARCHAR(200)
                    )""");
        }
    }

    public static JavAPI configure() {
        return configure(DEMO_DB_URL);
    }

    public static JavAPI configure(String dbUrl) {
        return JavAPI.create()
                .get("/", request -> Map.of("Hello", "World"))
                .get("/slow", request -> {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return Map.of("slow", true);
                })
                .get("/fast", request -> Map.of("fast", true))
                .cors(Cors.config().origins("http://localhost:5173"))
                .staticFiles("/static")
                .ws("/echo", new EchoEndpoint())
                .component(Greeter.class, EnglishGreeter.class)
                .jdbc(dbUrl, "sa", "")
                .scan("demo")
                .port(8000)
                .logRequests(true);
    }

    public static void main(String[] args) throws Exception {
        initSchema(DEMO_DB_URL);
        configure().start().await();
    }
}
