package javapi.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javapi.routing.RouteScanner;
import javapi.routing.Router;
import javapi.testroutes.DemoController;

class RoutingIntegrationTest {

    private NettyServer server;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private int port;

    @BeforeEach
    void setUp() {
        Router router = RouteScanner.scan(
                new Router(), "javapi.testroutes", getClass().getClassLoader());
        server = new NettyServer(router, 0);
        server.start();
        port = server.port();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private HttpResponse<String> request(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(5))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void getListReturns200() throws Exception {
        HttpResponse<String> response = request("GET", "/items");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("alpha") || response.body().contains("true"));
    }

    @Test
    void getByIdReturns200() throws Exception {
        assertEquals(200, request("GET", "/items/42").statusCode());
    }

    @Test
    void pathParamIsBoundAndCoerced() throws Exception {
        HttpResponse<String> response = request("GET", "/items/42");
        assertTrue(response.body().contains("\"itemId\":42"), "expected bound itemId, got: " + response.body());
    }

    @Test
    void nonNumericPathParamReturns422() throws Exception {
        HttpResponse<String> response = request("GET", "/items/abc");
        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("int_parsing"), "expected int_parsing detail, got: " + response.body());
    }

    @Test
    void nonNumericQueryReturns422() throws Exception {
        HttpResponse<String> response = request("GET", "/items?limit=abc");
        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("int_parsing"), "expected int_parsing detail, got: " + response.body());
    }

    @Test
    void missingMandatoryQueryReturns422() throws Exception {
        HttpResponse<String> response = request("GET", "/items/search");
        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("missing"), "expected missing detail, got: " + response.body());
    }

    @Test
    void mandatoryQueryBinds() throws Exception {
        HttpResponse<String> response = request("GET", "/items/search?q=needle");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("needle"), "got: " + response.body());
    }

    @Test
    void bodyIsParsedIntoRecord() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/items"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"bolt\",\"qty\":3}"))
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"name\":\"bolt\""), "got: " + response.body());
        assertTrue(response.body().contains("\"qty\":3"), "got: " + response.body());
    }

    @Test
    void invalidJsonBodyReturns422() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/items"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":"))
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, response.statusCode());
    }

    @Test
    void postReturns200() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/items"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"a\",\"qty\":1}"))
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    @Test
    void emptyBodyReturns422() throws Exception {
        assertEquals(422, request("POST", "/items").statusCode());
    }

    @Test
    void headRouteReturns200() throws Exception {
        HttpResponse<String> response = request("HEAD", "/items/ping");
        assertEquals(200, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    void unknownPathReturns404() throws Exception {
        assertEquals(404, request("GET", "/nope").statusCode());
    }

    @Test
    void wrongMethodReturns405WithAllowHeader() throws Exception {
        HttpResponse<String> response = request("DELETE", "/items");
        assertEquals(405, response.statusCode());
        String allow = response.headers().firstValue("allow").orElse("");
        assertTrue(allow.contains("GET"), "Allow header missing GET, was: " + allow);
        assertTrue(allow.contains("POST"), "Allow header missing POST, was: " + allow);
    }

    @Test
    void customResponseStatusHeadersAndBody() throws Exception {
        HttpResponse<String> response = request("POST", "/items/custom");
        assertEquals(201, response.statusCode());
        assertEquals("test", response.headers().firstValue("X-Created-By").orElse(""));
        assertTrue(response.body().contains("\"created\":true"), "got: " + response.body());
    }

    @Test
    void httpExceptionMapsToItsStatus() throws Exception {
        HttpResponse<String> response = request("GET", "/items/throw-404");
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("No such item"), "got: " + response.body());
        assertTrue(response.body().contains("\"detail\""), "got: " + response.body());
    }

    @Test
    void unknownExceptionMapsTo500WithJsonErrorBody() throws Exception {
        HttpResponse<String> response = request("GET", "/items/throw-500");
        assertEquals(500, response.statusCode());
        assertTrue(response.body().contains("kaboom"), "got: " + response.body());
        assertTrue(response.body().contains("\"detail\""), "got: " + response.body());
    }

    @Test
    void backgroundTaskRunsAfterResponse() throws Exception {
        int before = DemoController.backgroundRuns;
        request("GET", "/items/background");
        awaitTrue(() -> DemoController.backgroundRuns > before, "background task did not run");
    }

    private static void awaitTrue(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        fail(message);
    }
}
