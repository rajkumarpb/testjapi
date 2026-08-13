package javapi.testkit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import javapi.annotations.HttpMethod;
import javapi.core.DocsProvider;
import javapi.core.JavAPI;
import javapi.core.Server;
import javapi.core.ServerFactory;
import javapi.core.ServerSettings;
import javapi.json.Json;
import javapi.routing.Router;

/**
 * An in-process HTTP client for testing a {@link Router} or {@link JavAPI} app.
 *
 * <p>The server is started on an ephemeral loopback port and every request goes
 * through the real Netty pipeline, so tests exercise the full framework without
 * any external dependency.</p>
 *
 * <pre>{@code
 * try (TestClient client = TestClient.forApp(app)) {
 *     TestResponse res = client.get("/items/1");
 *     assertEquals(200, res.status());
 *     assertTrue(res.json().toString().contains("name"));
 * }
 * }</pre>
 */
public final class TestClient implements AutoCloseable {

    private final Server server;
    private final HttpClient http;
    private final int port;

    public static TestClient forApp(JavAPI app) {
        return new TestClient(app.router());
    }

    public TestClient(Router router) {
        for (DocsProvider provider : ServiceLoader.load(DocsProvider.class)) {
            provider.install(router);
        }
        ServerFactory factory = ServiceLoader.load(ServerFactory.class).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No ServerFactory implementation found on the classpath"));
        ServerSettings settings = new ServerSettings("localhost", 0, 0, false, false);
        server = factory.create(router, settings).start();
        port = server.port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public int port() {
        return port;
    }

    public String baseUrl() {
        return "http://localhost:" + port;
    }

    public TestResponse get(String path) {
        return request(HttpMethod.GET, path, Map.of(), null, (byte[]) null);
    }

    public TestResponse get(String path, Map<String, String> headers) {
        return request(HttpMethod.GET, path, headers, null, (byte[]) null);
    }

    public TestResponse delete(String path) {
        return request(HttpMethod.DELETE, path, Map.of(), null, (byte[]) null);
    }

    public TestResponse delete(String path, Map<String, String> headers) {
        return request(HttpMethod.DELETE, path, headers, null, (byte[]) null);
    }

    public TestResponse options(String path, Map<String, String> headers) {
        return request(HttpMethod.OPTIONS, path, headers, null, (byte[]) null);
    }

    public TestResponse head(String path) {
        return request(HttpMethod.HEAD, path, Map.of(), null, (byte[]) null);
    }

    public TestResponse post(String path) {
        return request(HttpMethod.POST, path, Map.of(), null, (byte[]) null);
    }

    public TestResponse post(String path, Object jsonBody) {
        return request(HttpMethod.POST, path, Map.of(), "application/json", Json.write(jsonBody));
    }

    public TestResponse post(String path, Map<String, String> headers, Object jsonBody) {
        return request(HttpMethod.POST, path, headers, "application/json", Json.write(jsonBody));
    }

    public TestResponse post(String path, String contentType, String body) {
        return request(HttpMethod.POST, path, Map.of(), contentType, body);
    }

    public TestResponse put(String path, Object jsonBody) {
        return request(HttpMethod.PUT, path, Map.of(), "application/json", Json.write(jsonBody));
    }

    public TestResponse put(String path, String contentType, String body) {
        return request(HttpMethod.PUT, path, Map.of(), contentType, body);
    }

    public TestResponse patch(String path, Object jsonBody) {
        return request(HttpMethod.PATCH, path, Map.of(), "application/json", Json.write(jsonBody));
    }

    public TestResponse patch(String path, String contentType, String body) {
        return request(HttpMethod.PATCH, path, Map.of(), contentType, body);
    }

    public TestResponse postForm(String path, Map<String, String> fields) {
        return request(HttpMethod.POST, path, Map.of(), "application/x-www-form-urlencoded", encodeForm(fields));
    }

    public TestResponse postMultipart(String path, Map<String, String> form, Map<String, TestFile> files) {
        String boundary = "javapi-" + Long.toHexString(System.nanoTime());
        byte[] body = buildMultipart(boundary, form, files);
        return request(HttpMethod.POST, path, Map.of(),
                "multipart/form-data; boundary=" + boundary, body);
    }

    public TestResponse postMultipart(String path, Map<String, String> headers,
            Map<String, String> form, Map<String, TestFile> files) {
        String boundary = "javapi-" + Long.toHexString(System.nanoTime());
        byte[] body = buildMultipart(boundary, form, files);
        return request(HttpMethod.POST, path, headers,
                "multipart/form-data; boundary=" + boundary, body);
    }

    public TestResponse request(HttpMethod method, String path,
            Map<String, String> headers, String contentType, String body) {
        byte[] bytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
        return request(method, path, headers, contentType, bytes);
    }

    public TestResponse request(HttpMethod method, String path,
            Map<String, String> headers, String contentType, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + (path.startsWith("/") ? path : "/" + path)))
                .timeout(Duration.ofSeconds(30));
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        switch (method) {
            case GET -> builder.GET();
            case HEAD -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            case POST -> builder.POST(publisher);
            case PUT -> builder.PUT(publisher);
            case DELETE -> builder.DELETE();
            default -> builder.method(method.name(), publisher);
        }
        if (contentType != null && body != null) {
            builder.header("Content-Type", contentType);
        }
        headers.forEach(builder::header);
        HttpResponse<byte[]> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Request to " + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Request to " + path + " interrupted", e);
        }
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) ->
                normalized.put(name.toLowerCase(Locale.ROOT), List.copyOf(values)));
        return new TestResponse(response.statusCode(), normalized, response.body());
    }

    @Override
    public void close() {
        server.close();
    }

    private static String encodeForm(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static byte[] buildMultipart(String boundary,
            Map<String, String> form, Map<String, TestFile> files) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] delimiter = ("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : form.entrySet()) {
                out.write(delimiter);
                String headers = "Content-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.UTF_8));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            for (Map.Entry<String, TestFile> entry : files.entrySet()) {
                TestFile file = entry.getValue();
                out.write(delimiter);
                String headers = "Content-Disposition: form-data; name=\"" + entry.getKey()
                        + "\"; filename=\"" + file.filename() + "\"\r\n"
                        + "Content-Type: " + file.contentType() + "\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.UTF_8));
                out.write(file.content());
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
