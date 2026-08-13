# 14. Testing

The `testkit` module ships an **in-process** `TestClient`: your app runs on a
real Netty pipeline on an ephemeral port, and tests make real HTTP calls —
nothing to mock, nothing external to start.

> **What you'll learn**
>
> - Starting a `TestClient` for your app
> - The request helpers (JSON, forms, multipart)
> - The `TestResponse` API for assertions

## First test

```groovy
dependencies {
    testImplementation "dev.javapi:javapi-testkit:0.1.0"
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
```

```java
import javapi.core.JavAPI;
import javapi.testkit.TestClient;
import javapi.testkit.TestResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemControllerTest {

    private final JavAPI app = JavAPI.create()
            .get("/items", request -> Map.of("items", List.of("alpha", "beta")))
            .post("/items", request -> Map.of("created", true));

    @Test
    void listsItems() {
        try (TestClient client = TestClient.forApp(app)) {
            TestResponse res = client.get("/items");
            assertEquals(200, res.status());
            assertTrue(res.ok());
            assertTrue(res.text().contains("\"alpha\""));
        }
    }
}
```

`TestClient.forApp(app)` installs the docs provider, starts the server on an
ephemeral loopback port, and is `AutoCloseable` — the `try`-with-resources shuts
the server down. If you built a `Router` directly, `new TestClient(router)`
works the same way. For a scanned app, just call
`TestClient.forApp(JavAPI.create().scan("com.example"))`.

## Requests

| Helper                                    | What it sends                          |
|-------------------------------------------|----------------------------------------|
| `get(path)` / `get(path, headers)`        | GET                                    |
| `post(path)` / `post(path, jsonBody)`     | POST, optionally JSON body             |
| `put(path, jsonBody)` / `patch(path, jsonBody)` | PUT / PATCH with JSON          |
| `delete(path)` / `head(path)` / `options(path, headers)` | the rest                  |
| `postForm(path, Map<String,String>)`      | urlencoded form                        |
| `postMultipart(path, form, files)`        | multipart with `TestFile`s             |
| `request(method, path, headers, contentType, body)` | anything                     |

JSON bodies are serialized with the same `Json` machinery as responses, so
records work directly:

```java
client.post("/items", new Item("widget", 3, null));
```

Multipart example:

```java
import javapi.testkit.TestFile;

TestResponse res = client.postMultipart("/upload",
        Map.of("note", "quarterly report"),
        Map.of("document", TestFile.of("report.pdf", "application/pdf", "PDF bytes...")));
```

## Assertions

`TestResponse` exposes:

```java
res.status();                  // int
res.ok();                      // status in 2xx
res.text();                    // body as String
res.body();                    // raw bytes
res.json();                    // parsed (Map/List/Object)
res.json(Item.class);          // parsed into a record
res.headers();                 // Map<String, List<String>> (lowercased)
res.header("content-type");    // Optional<String>, case-insensitive
res.contentType();             // String
```

Typed parsing makes assertions read nicely (here against a scanned controller
whose `@Post` returns `Response.of(201, item)` for a `@Body Item`):

```java
record Item(String name, int quantity, String supplierEmail) {}

@Test
void createsItem() {
    try (TestClient client = TestClient.forApp(app)) {
        TestResponse res = client.post("/items", Map.of(
                "name", "widget",
                "quantity", 3,
                "supplierEmail", "vendor@example.com"));
        assertEquals(201, res.status());
        Item created = res.json(Item.class);
        assertEquals("widget", created.name());
    }
}
```

## Verifying failures

Test the validation behavior too:

```java
@Test
void rejectsShortNames() {
    try (TestClient client = TestClient.forApp(app)) {
        TestResponse res = client.post("/items", Map.of("name", "x", "quantity", 3));
        assertEquals(422, res.status());
        assertTrue(res.text().contains("\"loc\""));
    }
}
```

Because the client talks to the real HTTP pipeline, tests cover routing,
binding, validation, middleware, and serialization end-to-end — no behavioral
gaps between unit and integration tests.

Next up: [15. CLI & deployment](15-cli-deploy.md).
