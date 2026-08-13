package javapi.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javapi.annotations.HttpMethod;
import javapi.request.HttpException;
import javapi.routing.ExecutionMode;
import javapi.routing.Router;

class AsyncIntegrationTest {

    private NettyServer server;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private int port;

    @BeforeEach
    void setUp() {
        Router router = new Router();
        router.register(HttpMethod.GET, "/slow",
                request -> {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return Map.of("slow", true);
                });
        router.register(HttpMethod.GET, "/fast", request -> Map.of("fast", true));
        router.register(Set.of(HttpMethod.GET), "/inline", request -> Map.of("inline", true),
                null, ExecutionMode.EVENT_LOOP);
        router.register(HttpMethod.GET, "/async",
                request -> CompletableFuture.completedFuture(Map.of("async", true)));
        router.register(HttpMethod.GET, "/async-error",
                request -> CompletableFuture.failedFuture(new HttpException(418, "teapot")));
        server = new NettyServer(router, 0);
        server.start();
        port = server.port();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
    }

    @Test
    void slowEndpointDoesNotStallOtherRequests() throws Exception {
        CompletableFuture<HttpResponse<String>> slow =
                client.sendAsync(request("/slow"), HttpResponse.BodyHandlers.ofString());
        long start = System.nanoTime();
        HttpResponse<String> fast = client.send(request("/fast"), HttpResponse.BodyHandlers.ofString());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals(200, fast.statusCode());
        assertTrue(elapsedMs < 900, "fast request waited for slow endpoint: " + elapsedMs + "ms");
        HttpResponse<String> slowResponse = slow.get(3, TimeUnit.SECONDS);
        assertEquals(200, slowResponse.statusCode());
        assertTrue(slowResponse.body().contains("\"slow\":true"), "got: " + slowResponse.body());
    }

    @Test
    void inlineEndpointServesOnEventLoop() throws Exception {
        HttpResponse<String> response = client.send(request("/inline"), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"inline\":true"), "got: " + response.body());
    }

    @Test
    void futureReturnValueIsAwaited() throws Exception {
        HttpResponse<String> response = client.send(request("/async"), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"async\":true"), "got: " + response.body());
    }

    @Test
    void failedFutureMapsThroughExceptionMapper() throws Exception {
        HttpResponse<String> response = client.send(request("/async-error"), HttpResponse.BodyHandlers.ofString());
        assertEquals(418, response.statusCode());
        assertTrue(response.body().contains("teapot"), "got: " + response.body());
    }

    @Test
    void closeWaitsForInFlightRequests() throws Exception {
        CompletableFuture<HttpResponse<String>> slow =
                client.sendAsync(request("/slow"), HttpResponse.BodyHandlers.ofString());
        Thread.sleep(150);
        long start = System.nanoTime();
        server.close();
        long closeMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(closeMs < 5000, "close took too long: " + closeMs + "ms");
        HttpResponse<String> response = slow.get(3, TimeUnit.SECONDS);
        assertEquals(200, response.statusCode());
    }
}
