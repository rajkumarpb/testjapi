package javapi.middleware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.Map;
import org.junit.jupiter.api.Test;
import javapi.request.Request;
import javapi.request.Response;

class CorsTest {

    private static Request corsRequest(String method, Map<String, String> headers) {
        return Request.builder()
                .method(method)
                .path("/api")
                .headers(headers)
                .build();
    }

    private static Response call(Cors cors, Request request) {
        return (Response) cors.handle(request, req -> Response.ok("{\"ok\":true}"));
    }

    @Test
    void preflightReturns204WithCorsHeaders() {
        Cors cors = Cors.config();
        Response response = call(cors, corsRequest("OPTIONS", Map.of(
                "Origin", "https://app.example",
                "Access-Control-Request-Method", "POST")));
        assertEquals(204, response.status());
        assertEquals("*", response.headers().get("Access-Control-Allow-Origin"));
        assertNull(response.headers().get("Access-Control-Allow-Credentials"));
        assertEquals("GET, POST, PUT, DELETE, PATCH, OPTIONS",
                response.headers().get("Access-Control-Allow-Methods"));
    }

    @Test
    void actualRequestEchoesSpecificOrigin() {
        Cors cors = Cors.config().origins("https://app.example");
        Response response = call(cors, corsRequest("GET", Map.of("Origin", "https://app.example")));
        assertEquals("https://app.example", response.headers().get("Access-Control-Allow-Origin"));
        assertEquals("Origin", response.headers().get("Vary"));
    }

    @Test
    void disallowedOriginGetsNoCorsHeaders() {
        Cors cors = Cors.config().origins("https://app.example");
        Response response = call(cors, corsRequest("GET", Map.of("Origin", "https://evil.example")));
        assertNull(response.headers().get("Access-Control-Allow-Origin"));
    }

    @Test
    void credentialsModeAddsAllowCredentials() {
        Cors cors = Cors.config().credentials(true).origins("https://app.example");
        Response response = call(cors, corsRequest("GET", Map.of("Origin", "https://app.example")));
        assertEquals("true", response.headers().get("Access-Control-Allow-Credentials"));
        assertEquals("https://app.example", response.headers().get("Access-Control-Allow-Origin"));
    }

    @Test
    void wildcardOriginsWithCredentialsEchoesOrigin() {
        Cors cors = Cors.config().credentials(true);
        Response response = call(cors, corsRequest("GET", Map.of("Origin", "https://app.example")));
        assertEquals("https://app.example", response.headers().get("Access-Control-Allow-Origin"));
        assertEquals("true", response.headers().get("Access-Control-Allow-Credentials"));
    }

    @Test
    void customHeadersAreReflectedOnPreflight() {
        Cors cors = Cors.config().headers("X-Custom", "X-Other");
        Response response = call(cors, corsRequest("OPTIONS", Map.of(
                "Origin", "https://app.example",
                "Access-Control-Request-Method", "GET")));
        assertEquals("X-Custom, X-Other", response.headers().get("Access-Control-Allow-Headers"));
    }

    @Test
    void nonCorsRequestPassesThroughUnchanged() {
        Cors cors = Cors.config();
        Response response = call(cors, corsRequest("GET", Map.of()));
        assertEquals(200, response.status());
        assertNull(response.headers().get("Access-Control-Allow-Origin"));
    }

    @Test
    void passesThroughNonResponseResults() {
        Cors cors = Cors.config();
        Object result = cors.handle(
                corsRequest("GET", Map.of("Origin", "https://app.example")),
                req -> "plain");
        assertEquals("plain", result);
    }
}
