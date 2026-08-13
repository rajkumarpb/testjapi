package javapi.staticfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javapi.request.Request;
import javapi.request.Response;

class StaticFilesTest {

    @TempDir
    Path tempDir;

    private static Response serve(StaticFiles files, String path) {
        Object result = files.handle(
                Request.builder().method("GET").path(path).build(),
                req -> Response.of(404, "not found"));
        return (Response) result;
    }

    @Test
    void servesFileFromDirectoryWithContentType() throws Exception {
        Files.writeString(tempDir.resolve("hello.txt"), "hi there", StandardCharsets.UTF_8);
        StaticFiles files = StaticFiles.fromDirectory("/static", tempDir);
        Response response = serve(files, "/static/hello.txt");
        assertEquals(200, response.status());
        assertEquals("hi there", new String((byte[]) response.body(), StandardCharsets.UTF_8));
        assertEquals("text/plain; charset=utf-8", response.headers().get("Content-Type"));
    }

    @Test
    void servesIndexHtmlForDirectoryRoot() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "<h1>Home</h1>", StandardCharsets.UTF_8);
        StaticFiles files = StaticFiles.fromDirectory("/", tempDir);
        Response response = serve(files, "/");
        assertEquals(200, response.status());
        assertEquals("<h1>Home</h1>", new String((byte[]) response.body(), StandardCharsets.UTF_8));
    }

    @Test
    void missingFileDefersToNext() throws Exception {
        StaticFiles files = StaticFiles.fromDirectory("/static", tempDir);
        Response response = serve(files, "/static/nope.txt");
        assertEquals(404, response.status());
    }

    @Test
    void pathTraversalIsRejected() throws Exception {
        Files.writeString(tempDir.resolve("secret.txt"), "secret", StandardCharsets.UTF_8);
        StaticFiles files = StaticFiles.fromDirectory("/static", tempDir);
        Response response = serve(files, "/static/../secret.txt");
        assertEquals(404, response.status());
    }

    @Test
    void nonMatchingPrefixDefersToNext() throws Exception {
        StaticFiles files = StaticFiles.fromDirectory("/static", tempDir);
        Response response = serve(files, "/other/file.txt");
        assertEquals(404, response.status());
    }

    @Test
    void unknownExtensionGetsOctetStream() throws Exception {
        Files.writeString(tempDir.resolve("data.bin"), "xyz", StandardCharsets.UTF_8);
        StaticFiles files = StaticFiles.fromDirectory("/static", tempDir);
        Response response = serve(files, "/static/data.bin");
        assertEquals("application/octet-stream", response.headers().get("Content-Type"));
    }

    @Test
    void servesFromClasspath() {
        StaticFiles files = StaticFiles.fromClasspath("/static", getClass().getClassLoader());
        Response response = serve(files, "/static/hello.txt");
        assertEquals(200, response.status());
        assertEquals("hello from classpath",
                new String((byte[]) response.body(), StandardCharsets.UTF_8).trim());
    }

    @Test
    void missingClasspathFileDefersToNext() {
        StaticFiles files = StaticFiles.fromClasspath("/static", getClass().getClassLoader());
        Response missing = serve(files, "/static/does-not-exist.txt");
        assertEquals(404, missing.status());
    }
}
