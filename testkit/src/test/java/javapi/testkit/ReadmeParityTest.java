package javapi.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import javapi.core.JavAPI;
import javapi.middleware.Cors;
import javapi.readme.EnglishGreeter;
import javapi.readme.Greeter;

/**
 * Parity suite: exercises the exact examples documented in the README quickstart.
 */
class ReadmeParityTest {

    private JavAPI app() {
        return JavAPI.create()
                .component(Greeter.class, EnglishGreeter.class)
                .cors(Cors.config().origins("https://spa.example"))
                .staticFiles("/static")
                .get("/", request -> Map.of("Hello", "World"))
                .scan("javapi.readme");
    }

    @Test
    void helloWorldRoute() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.get("/");
            assertEquals(200, res.status());
            assertEquals("World", res.json(Map.class).get("Hello"));
        }
    }

    @Test
    void listBindsQueryParams() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.get("/items?limit=10&q=alph");
            assertEquals(200, res.status());
            Map<?, ?> body = res.json(Map.class);
            assertEquals(10L, body.get("limit"));
            assertEquals("alph", body.get("q"));
            assertTrue(res.text().contains("\"alpha\""));
        }
    }

    @Test
    void listRequiresLimit() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.get("/items");
            assertEquals(422, res.status());
        }
    }

    @Test
    void itemBindsPathAndHeader() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.get("/items/42", Map.of("User-Agent", "parity-agent"));
            assertEquals(200, res.status());
            Map<?, ?> body = res.json(Map.class);
            assertEquals(42L, body.get("itemId"));
            assertEquals("parity-agent", body.get("userAgent"));
        }
    }

    @Test
    void createAcceptsValidBody() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.post("/items",
                    Map.of("name", "gadget", "quantity", 5, "supplierEmail", "acme@example.com"));
            assertEquals(200, res.status());
            assertTrue(res.text().contains("\"gadget\""));
        }
    }

    @Test
    void createRejectsTooShortName() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.post("/items",
                    Map.of("name", "x", "quantity", 5));
            assertEquals(422, res.status());
            assertTrue(res.text().contains("name"), "got: " + res.text());
        }
    }

    @Test
    void createRejectsQuantityOutOfRange() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.post("/items",
                    Map.of("name", "gadget", "quantity", 5000));
            assertEquals(422, res.status());
            assertTrue(res.text().contains("quantity"), "got: " + res.text());
        }
    }

    @Test
    void createRejectsInvalidEmail() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.post("/items",
                    Map.of("name", "gadget", "quantity", 5, "supplierEmail", "not-an-email"));
            assertEquals(422, res.status());
            assertTrue(res.text().contains("supplierEmail"), "got: " + res.text());
        }
    }

    @Test
    void missingResourceRaisesHttpException() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.get("/items/missing");
            assertEquals(404, res.status());
            assertTrue(res.text().contains("Item not found"), "got: " + res.text());
        }
    }

    @Test
    void responseStatusAndCustomHeader() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.get("/items/status");
            assertEquals(202, res.status());
            assertEquals("demo-123", res.header("X-Request-Id").orElse(""));
            assertTrue(res.text().contains("\"accepted\":true"));
        }
    }

    @Test
    void exceptionMapperReturns500Json() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.get("/items/boom");
            assertEquals(500, res.status());
            assertEquals("illegal_state", res.json(Map.class).get("error"));
        }
    }

    @Test
    void dependencyInjectionAcrossScannedRoutes() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.get("/items/hello?name=Ada");
            assertEquals(200, res.status());
            assertEquals("Hello, Ada", res.json(Map.class).get("greeting"));
        }
    }

    @Test
    void corsPreflightAndActualRequest() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse preflight = client.options("/items",
                    Map.of("Origin", "https://spa.example", "Access-Control-Request-Method", "POST"));
            assertEquals(204, preflight.status());
            assertEquals("https://spa.example", preflight.header("Access-Control-Allow-Origin").orElse(""));
            assertTrue(preflight.header("Access-Control-Allow-Methods").orElse("").contains("POST"));

            TestResponse actual = client.get("/", Map.of("Origin", "https://spa.example"));
            assertEquals(200, actual.status());
            assertEquals("https://spa.example", actual.header("Access-Control-Allow-Origin").orElse(""));
        }
    }

    @Test
    void staticFilesServedFromClasspath() {
        try (TestClient client = TestClient.forApp(app())) {
            TestResponse res = client.get("/static/hello.txt");
            assertEquals(200, res.status());
            assertTrue(res.text().contains("static hello"), "got: " + res.text());
        }
    }
}
