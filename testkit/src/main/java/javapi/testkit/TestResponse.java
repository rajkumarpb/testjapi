package javapi.testkit;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javapi.json.Json;

/**
 * The result of a {@link TestClient} request.
 */
public final class TestResponse {

    private final int status;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    TestResponse(int status, Map<String, List<String>> headers, byte[] body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    public int status() {
        return status;
    }

    public boolean ok() {
        return status >= 200 && status < 300;
    }

    public byte[] body() {
        return body;
    }

    public String text() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public Optional<String> header(String name) {
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public String contentType() {
        return header("content-type").orElse("");
    }

    public Object json() {
        return Json.parse(text(), Object.class);
    }

    public <T> T json(Class<T> type) {
        return Json.parse(text(), type);
    }

    public <T> T json(Type type) {
        return Json.parse(text(), type);
    }
}
