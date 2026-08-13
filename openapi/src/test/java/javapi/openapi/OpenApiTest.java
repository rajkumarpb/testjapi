package javapi.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import javapi.annotations.HttpMethod;
import javapi.request.Request;
import javapi.request.Response;
import javapi.routing.Handler;
import javapi.routing.RouteScanner;
import javapi.routing.Router;

class OpenApiTest {

    private static Router scannedRouter() {
        return RouteScanner.scan(
                new Router(), "javapi.openapi.testroutes", OpenApiTest.class.getClassLoader());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paths(Map<String, Object> spec) {
        return (Map<String, Object>) spec.get("paths");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getOperation(Map<String, Object> paths, String path, String method) {
        return (Map<String, Object>) ((Map<String, Object>) paths.get(path)).get(method);
    }

    @Test
    void generatesOpenApi30WithPaths() {
        Map<String, Object> spec = OpenApiGenerator.generate(scannedRouter());
        assertEquals("3.1.0", spec.get("openapi"));
        Map<String, Object> paths = paths(spec);
        assertTrue(paths.containsKey("/api/hello"));
        assertTrue(paths.containsKey("/api/{id}"));
        assertTrue(paths.containsKey("/api/create"));
    }

    @Test
    void colonPathParamsConvertedToBraces() {
        Map<String, Object> paths = paths(OpenApiGenerator.generate(scannedRouter()));
        assertTrue(paths.containsKey("/api/{id}"));
        assertFalse(paths.containsKey("/api/:id"));
    }

    @Test
    void pathParameterIsRequiredWithIntegerSchema() {
        Map<String, Object> paths = paths(OpenApiGenerator.generate(scannedRouter()));
        Map<String, Object> get = getOperation(paths, "/api/{id}", "get");
        List<Map<String, Object>> parameters = castList(get.get("parameters"));
        assertEquals(1, parameters.size());
        Map<String, Object> param = parameters.get(0);
        assertEquals("path", param.get("in"));
        assertEquals("id", param.get("name"));
        assertEquals(true, param.get("required"));
        assertEquals("integer", castMap(param.get("schema")).get("type"));
    }

    @Test
    void optionalQueryParameterIsNotRequired() {
        Map<String, Object> paths = paths(OpenApiGenerator.generate(scannedRouter()));
        Map<String, Object> get = getOperation(paths, "/api/hello", "get");
        List<Map<String, Object>> parameters = castList(get.get("parameters"));
        assertEquals(1, parameters.size());
        Map<String, Object> param = parameters.get(0);
        assertEquals("query", param.get("in"));
        assertEquals("name", param.get("name"));
        assertEquals(false, param.get("required"));
        assertEquals("string", castMap(param.get("schema")).get("type"));
    }

    @Test
    void bodyParamBecomesRequestBodySchemaWithConstraints() {
        Map<String, Object> paths = paths(OpenApiGenerator.generate(scannedRouter()));
        Map<String, Object> post = getOperation(paths, "/api/create", "post");
        Map<String, Object> requestBody = castMap(post.get("requestBody"));
        assertEquals(true, requestBody.get("required"));
        Map<String, Object> schema = schemaOf(requestBody);
        assertEquals("object", schema.get("type"));
        Map<String, Object> properties = castMap(schema.get("properties"));
        assertTrue(properties.containsKey("name"));
        assertEquals(2, castMap(properties.get("name")).get("minLength"));
        List<String> required = castList(schema.get("required"));
        assertTrue(required.contains("name"));
        assertTrue(required.contains("qty"));
        assertFalse(required.contains("note"));
    }

    @Test
    void stringResponseGetsStringSchema() {
        Map<String, Object> paths = paths(OpenApiGenerator.generate(scannedRouter()));
        Map<String, Object> get = getOperation(paths, "/api/hello", "get");
        Map<String, Object> responses = castMap(get.get("responses"));
        Map<String, Object> ok = castMap(responses.get("200"));
        assertEquals("string", schemaOf(ok).get("type"));
    }

    @Test
    void internalDocsPathsAreExcluded() {
        Router router = scannedRouter();
        router.register(HttpMethod.GET, "/openapi.json", request -> Map.of());
        router.register(HttpMethod.GET, "/docs", request -> "");
        router.register(HttpMethod.GET, "/redoc", request -> "");
        Map<String, Object> paths = paths(OpenApiGenerator.generate(router));
        assertFalse(paths.containsKey("/openapi.json"));
        assertFalse(paths.containsKey("/docs"));
        assertFalse(paths.containsKey("/redoc"));
    }

    @Test
    void manualRoutesWithoutMetaAreIncluded() {
        Router router = scannedRouter();
        router.register(HttpMethod.GET, "/health", request -> "ok");
        Map<String, Object> paths = paths(OpenApiGenerator.generate(router));
        assertTrue(paths.containsKey("/health"));
    }

    @Test
    void specIsCachedAndRegeneratedOnRouteChange() {
        Router router = scannedRouter();
        Handler handler = OpenApi.specHandler(router);
        Request request = Request.builder().method("GET").uri("/openapi.json").build();
        Object first = handler.handle(request);
        Object second = handler.handle(request);
        assertSame(first, second);
        router.register(HttpMethod.GET, "/extra", r -> "x");
        Object third = handler.handle(request);
        assertNotSame(first, third);
        assertTrue(paths(castMap(third)).containsKey("/extra"));
    }

    @Test
    void docsHandlerServesHtml() {
        Request request = Request.builder().method("GET").uri("/docs").build();
        Object result = OpenApi.docsHandler().handle(request);
        assertTrue(result instanceof Response);
        Response response = (Response) result;
        assertEquals(200, response.status());
        assertTrue(response.headers().get("content-type").startsWith("text/html"));
        assertTrue(response.body().toString().contains("swagger-ui"));
        Object redoc = OpenApi.redocHandler().handle(request);
        assertTrue(((Response) redoc).body().toString().contains("redoc"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaOf(Map<String, Object> holder) {
        Map<String, Object> content = castMap(holder.get("content"));
        Map<String, Object> media = castMap(content.get("application/json"));
        return castMap(media.get("schema"));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castList(Object value) {
        return (List<T>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
