package javapi.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javapi.routing.RouteScanner;
import javapi.routing.Router;
import javapi.server.NettyServer;

class OpenApiServerIntegrationTest {

    private NettyServer server;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private int port;

    @BeforeEach
    void setUp() {
        Router router = RouteScanner.scan(
                new Router(), "javapi.openapi.testroutes", getClass().getClassLoader());
        new OpenApiDocsProvider().install(router);
        server = new NettyServer(router, 0);
        server.start();
        port = server.port();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private HttpResponse<String> request(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void openapiJsonServesSpec() throws Exception {
        HttpResponse<String> response = request("/openapi.json");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/json"));
        assertTrue(response.body().contains("\"openapi\":\"3.1.0\""), "got: " + response.body());
        assertTrue(response.body().contains("/api/{id}"), "got: " + response.body());
    }

    @Test
    void docsServesSwaggerUi() throws Exception {
        HttpResponse<String> response = request("/docs");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("text/html"));
        assertTrue(response.body().contains("swagger-ui"), "got: " + response.body());
    }

    @Test
    void redocServesReDoc() throws Exception {
        HttpResponse<String> response = request("/redoc");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("text/html"));
        assertTrue(response.body().contains("Redoc"), "got: " + response.body());
    }
}
