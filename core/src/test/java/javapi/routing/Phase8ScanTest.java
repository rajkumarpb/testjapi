package javapi.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import javapi.annotations.HttpMethod;
import javapi.middleware.Middleware;
import javapi.params.UploadedFile;
import javapi.phase8routes.AppException;
import javapi.request.Request;
import javapi.request.Response;

class Phase8ScanTest {

    private final Router router = RouteScanner.scan(
            new Router(), "javapi.phase8routes", getClass().getClassLoader());

    private Request request(String method, String path) {
        return Request.builder().method(method).path(path).build();
    }

    @Test
    void exceptionMapperIsRegisteredAndApplied() {
        Throwable error = assertThrows(Throwable.class,
                () -> router.match(HttpMethod.GET, "/boom").route().handler()
                        .handle(request("GET", "/boom")));
        assertTrue(error instanceof AppException, "got: " + error);
        Response mapped = router.mapError(error);
        assertEquals(422, mapped.status());
        assertTrue(mapped.body().toString().contains("bad news"));
        assertTrue(mapped.body().toString().contains("app_error"));
    }

    @Test
    void middlewareClassesAreScannedAndRegistered() {
        assertFalse(router.middleware().isEmpty());
        Middleware middleware = router.middleware().get(0);
        Object result = middleware.handle(request("GET", "/"), req -> Response.ok("hi"));
        assertEquals("javapi", ((Response) result).headers().get("X-Powered-By"));
    }

    @Test
    void formAndFileParamsBindThroughScannedHandler() {
        Request upload = Request.builder()
                .method("POST")
                .path("/upload")
                .form(Map.of("note", "hello"))
                .files(List.of(new UploadedFile("document", "a.txt", "text/plain",
                        "abc".getBytes(StandardCharsets.UTF_8))))
                .build();
        Object result = router.match(HttpMethod.POST, "/upload").route().handler().handle(upload);
        String text = result.toString();
        assertTrue(text.contains("hello"), "got: " + text);
        assertTrue(text.contains("a.txt"), "got: " + text);
        assertTrue(text.contains("text/plain"), "got: " + text);
        assertTrue(text.contains("size=3"), "got: " + text);
        assertTrue(text.contains("abc"), "got: " + text);
    }

    @Test
    void valueAnnotationBindsFromConfig() {
        System.setProperty("javapi.feature.flag", "true");
        try {
            Object result = router.match(HttpMethod.GET, "/config").route().handler()
                    .handle(request("GET", "/config"));
            assertTrue(result.toString().contains("true"), "got: " + result);
        } finally {
            System.clearProperty("javapi.feature.flag");
        }
    }
}
