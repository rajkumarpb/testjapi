package javapi.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import javapi.annotations.HttpMethod;
import javapi.core.JavAPI;
import javapi.routing.RouteScanner;
import javapi.routing.Router;
import javapi.testkitroutes.ItemController.Item;
import javapi.testkitroutes.ItemController.UploadResult;

class TestClientTest {

    private Router scannedRouter() {
        return RouteScanner.scan(new Router(), "javapi.testkitroutes", getClass().getClassLoader());
    }

    @Test
    void getPathParameterReturnsJsonRecord() {
        try (TestClient client = new TestClient(scannedRouter())) {
            TestResponse res = client.get("/items/7");
            assertEquals(200, res.status());
            assertTrue(res.ok());
            assertTrue(res.contentType().startsWith("application/json"), "got: " + res.contentType());
            Item item = res.json(Item.class);
            assertEquals(7, item.id());
            assertEquals("item-7", item.name());
            assertTrue(res.text().contains("\"id\":7"));
            assertInstanceOf(Map.class, res.json());
        }
    }

    @Test
    void queryParameterBinds() {
        try (TestClient client = new TestClient(scannedRouter())) {
            TestResponse res = client.get("/items/echo?q=hello%20world");
            assertEquals(200, res.status());
            assertEquals("hello world", res.json(String.class));
        }
    }

    @Test
    void missingRequiredQueryParameterReturnsValidationError() {
        try (TestClient client = new TestClient(scannedRouter())) {
            TestResponse res = client.get("/items/echo");
            assertEquals(422, res.status());
            assertFalse(res.ok());
            assertTrue(res.contentType().startsWith("application/json"), "got: " + res.contentType());
        }
    }

    @Test
    void postJsonBodyBindsAndParsesResponse() {
        try (TestClient client = new TestClient(scannedRouter())) {
            Map<String, Object> body = Map.of("id", 5L, "name", "widget", "price", 1.25);
            TestResponse res = client.post("/items", body);
            assertEquals(200, res.status());
            Item created = res.json(Item.class);
            assertEquals(6, created.id());
            assertEquals("widget", created.name());
        }
    }

    @Test
    void putPatchDeleteJson() {
        Router router = new Router();
        router.register(HttpMethod.PUT, "/items", r -> Map.of("method", "put"));
        router.register(HttpMethod.PATCH, "/items", r -> Map.of("method", "patch"));
        router.register(HttpMethod.DELETE, "/items/:id", r -> Map.of("deleted", true));
        try (TestClient client = new TestClient(router)) {
            TestResponse put = client.put("/items", Map.of("id", 1L));
            assertEquals(200, put.status());
            assertTrue(put.text().contains("put"));
            TestResponse patch = client.patch("/items", Map.of("id", 1L));
            assertEquals(200, patch.status());
            assertTrue(patch.text().contains("patch"));
            TestResponse delete = client.delete("/items/3");
            assertEquals(200, delete.status());
            assertTrue(delete.text().contains("\"deleted\":true"));
        }
    }

    @Test
    void formAndFileUpload() {
        try (TestClient client = new TestClient(scannedRouter())) {
            TestResponse res = client.postMultipart("/items/upload",
                    Map.of("note", "hello upload"),
                    Map.of("document", TestFile.of("report.txt", "text/plain", "file contents")));
            assertEquals(200, res.status());
            UploadResult result = res.json(UploadResult.class);
            assertEquals("hello upload", result.note());
            assertEquals("report.txt", result.filename());
            assertEquals(13, result.size());
        }
    }

    @Test
    void formUrlEncoded() {
        try (TestClient client = new TestClient(scannedRouter())) {
            TestResponse res = client.postForm("/items/form", Map.of("note", "plain form"));
            assertEquals(200, res.status());
            assertEquals("plain form", res.json(String.class));
        }
    }

    @Test
    void exceptionBecomesErrorStatus() {
        try (TestClient client = new TestClient(scannedRouter())) {
            TestResponse res = client.get("/items/boom");
            assertEquals(418, res.status());
        }
    }

    @Test
    void headAndOptionsWork() {
        Router router = new Router();
        router.register(HttpMethod.HEAD, "/m", r -> Map.of("x", 1));
        router.register(HttpMethod.OPTIONS, "/m", r -> javapi.request.Response.status(204));
        try (TestClient client = new TestClient(router)) {
            TestResponse head = client.head("/m");
            assertEquals(200, head.status());
            assertEquals("", head.text());
            TestResponse options = client.options("/m", Map.of());
            assertEquals(204, options.status());
            assertEquals("", options.text());
        }
    }

    @Test
    void genericRequestAllowsArbitraryMethodAndBody() {
        try (TestClient client = new TestClient(scannedRouter())) {
            TestResponse res = client.request(HttpMethod.POST, "/items", Map.of(),
                    "application/json", "{\"id\":2,\"name\":\"x\",\"price\":0.0}");
            assertEquals(200, res.status());
            assertEquals(3, res.json(Item.class).id());
        }
    }

    @Test
    void forAppBuildsServerFromJavAPI() {
        try (TestClient client = TestClient.forApp(JavAPI.create().get("/hello", r -> "hi"))) {
            assertEquals("hi", client.get("/hello").json(String.class));
        }
    }

    @Test
    void missingRouteReturns404() {
        try (TestClient client = new TestClient(scannedRouter())) {
            TestResponse res = client.get("/nope");
            assertEquals(404, res.status());
        }
    }

    @Test
    void headerAccessIsCaseInsensitive() {
        try (TestClient client = new TestClient(scannedRouter())) {
            TestResponse res = client.get("/items/1");
            assertEquals(res.header("Content-Type"), res.header("content-type"));
            assertTrue(res.header("content-type").orElse("").contains("json"));
            assertTrue(res.headers().containsKey("content-type"));
            assertEquals(List.of(res.header("content-type").get()), res.headers().get("content-type"));
        }
    }

    @Test
    void responseWithNullBodyAllowed() {
        try (TestClient client = TestClient.forApp(JavAPI.create().post("/empty", r -> null))) {
            TestResponse res = client.post("/empty");
            assertEquals(200, res.status());
            assertEquals("", res.text());
        }
    }
}
