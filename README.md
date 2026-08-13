# javapi

A FastAPI-inspired, type-driven REST framework for **Java 21**. You declare request
and response shapes with **plain type annotations**, and javapi handles routing,
parsing, validation, serialization, interactive docs, and more — at runtime, with
no code generation.

```java
import javapi.core.JavAPI;

public class Main {
    public static void main(String[] args) throws Exception {
        JavAPI.create()
                .get("/", request -> java.util.Map.of("Hello", "World"))
                .scan("com.example")
                .start()
                .await();
    }
}
```

Run it, then open `http://localhost:8080/docs` for interactive Swagger UI.

> **New here?** The [**tutorial**](docs/README.md) walks you from an empty
> directory to a deployed API — installation, routing, parameters, validation,
> DI, databases, and deployment — in 15 short chapters.

## Highlights

- **Runtime reflection over `record` types** — parse/validate/serialize from your
  type declarations alone. Zero codegen, zero annotations on types unless you want them.
- **All-lowercase annotations** — `@get`, `@route`, `@body`, `@query`, `@depends`, `@value`, ...
- **Java 21 virtual threads** — blocking handler code is fine; a slow endpoint never
  stalls the server.
- **Netty** under the hood.
- **OpenAPI 3** docs auto-generated at `/docs` and `/redoc`.

## Getting started

### Dependencies

```groovy
dependencies {
    implementation "dev.javapi:javapi-core:$javapiVersion"
    implementation "dev.javapi:javapi-server:$javapiVersion"     // Netty transport
    implementation "dev.javapi:javapi-openapi:$javapiVersion"    // /docs, /redoc
    implementation "dev.javapi:javapi-testkit:$javapiVersion"    // tests
    testImplementation "org.junit.jupiter:junit-jupiter:5.11.4"
}
```

### Your first API

`javapi` reflects over controller classes. `@route` on a class is a path prefix;
verb annotations register methods:

```java
package com.example;

import java.util.List;
import java.util.Map;
import javapi.annotations.*;

@route("/items")
public class ItemController {

    public record Item(
            @minlength(2) @maxlength(20) String name,
            @min(1) @max(1000) int quantity,
            @email @optional String supplierEmail) {
    }

    @get("/")
    public Map<String, Object> list(@query("limit") int limit, @query("q") @optional String q) {
        return Map.of("items", List.of("alpha", "beta"), "limit", limit, "q", q == null ? "" : q);
    }

    @get("/:itemId")
    public Map<String, Object> item(@path int itemId, @header("user-agent") @optional String userAgent) {
        return Map.of("itemId", itemId, "userAgent", userAgent == null ? "" : userAgent);
    }

    @post
    public Map<String, Object> create(@body Item item) {
        return Map.of("created", item);
    }
}
```

Register it in one line — the whole class is scanned and its routes wired up:

```java
JavAPI.create().scan("com.example").start().await();
```

## Parameters

Method parameters are bound from the request by annotation; type conversion and
validation happen automatically (422 with a JSON error body on failure).

| Annotation      | Source                    | Example                                  |
|-----------------|---------------------------|------------------------------------------|
| `@path`         | URL path segment          | `@get("/:itemId")` + `@path int itemId`  |
| `@query`        | Query string              | `@query("q") String q`                   |
| `@header`       | Request header            | `@header("user-agent") String ua`        |
| `@cookie`       | Cookie                    | `@cookie("session") String s`            |
| `@body`         | JSON request body         | `@body Item item`                        |
| `@form`         | Form-encoded field        | `@form("note") String note`              |
| `@file`         | Multipart file upload     | `@file UploadedFile doc`                 |
| `@value`        | Configuration value       | `@value("app.title") String title`       |
| `@depends`      | DI container lookup       | `@depends Greeter greeter`               |

`Optional<T>` parameters and `@optional`-annotated fields are nullable. `Request`
is injectable directly for raw access (`request.body()`, `request.form()`,
`request.files()`, `request.pathParam("id")`, `request.queryParams()`, ...).

## Validation

Constraint annotations work on parameters and record fields:

| Annotation    | Meaning                       |
|---------------|-------------------------------|
| `@min`/`@max` | numeric bounds                |
| `@minlength`/`@maxlength` | string length bounds |
| `@pattern`    | regex                         |
| `@email`      | email format                  |
| `@optional`   | nullable / optional           |

Failures return **422** with the field name and a `detail` message.

## Responses

Return anything serializable (records, enums, dates, `UUID`, lists, `Map`, ...) and
javapi serializes it as JSON. For full control use `Response`:

```java
return Response.status(202)
        .withBody(Map.of("accepted", true))
        .withHeader("X-Request-Id", "demo-123");
```

Raise `HttpException(status, message)` for a JSON error body with any status:

```java
throw new HttpException(404, "Item not found");
```

Map arbitrary exceptions to responses with `@exception`:

```java
@exception(IllegalStateException.class)
public Response onIllegalState(IllegalStateException error) {
    return Response.of(500, Map.of("detail", error.getMessage(), "error", "illegal_state"));
}
```

## Dependency injection

Register singletons and factories, then pull them into any endpoint with `@depends`:

```java
JavAPI.create()
        .component(Greeter.class, EnglishGreeter.class)
        .requestScoped(RequestContext.class, req -> new RequestContext(req))
        .scan("com.example")
        .start();
```

`JavAPI` supports `component(type, instance)`, `component(type, implClass)`,
`requestScoped(type, factory)`, and `override(type, instance)`.

## Middleware

`Middleware` is a function `Object handle(Request, Next)`; `Next` is
`Object next(Request)`. Call `next` to continue the chain, or return early.

```java
app.use((request, next) -> {
    long start = System.nanoTime();
    Object result = next.next(request);
    System.out.println(request.path() + " -> " + result + " in " + (System.nanoTime() - start) + "ns");
    return result;
});
```

Built-in middleware:

```java
app.cors(Cors.config().origins("http://localhost:5173"));      // CORS
app.staticFiles("/static");                                    // classpath resources
app.staticFiles("/uploads", Path.of("./uploads"));             // from disk
```

`@middleware` classes in a scanned package are registered automatically.

## File uploads

```java
@post("/upload")
public Response upload(@form String note, @file UploadedFile document) {
    return Response.ok(Map.of(
            "note", note,
            "filename", document.filename(),
            "size", document.size(),
            "contentType", document.contentType()));
}
```

## WebSockets

```java
app.ws("/echo", new WebSocketEndpoint() {
    @Override
    public void onOpen(WebSocketSession session) { session.send("welcome"); }
    @Override
    public void onMessage(WebSocketSession session, String message) {
        session.send("echo:" + message);
    }
    // onBinary, onClose, onError
});
```

## Server-sent events

Return an `SseEmitter` from any route:

```java
@get("/stream")
public SseEmitter stream() {
    SseEmitter emitter = new SseEmitter();
    for (int i = 0; i < 5; i++) {
        emitter.event("tick").data(Map.of("n", i));
    }
    emitter.complete();
    return emitter;
}
```

## Configuration

`javapi` reads `application.properties` (plus `application-{profile}.properties`) with
a first-hit-wins chain: code config > system properties (`-Djavapi.*`) > environment
(`JAVAPI_*`) > profile file > base file > defaults. Profile via
`-Djavapi.profile=prod` or `JAVAPI_PROFILE`.

```properties
server.port=8000
server.workers=8
app.title=javapi demo
app.workers=4
```

Endpoint configuration uses relaxed binding — `-Djavapi.workers=2` overrides
`app.workers` from the file, unless the code set it explicitly.

## JDBC & record mapping

With `core/jdbc`, inject a `Jdbc` helper and map result rows straight into records:

```java
@route("/db")
public class DbController {

    public record DbItem(long id, String name, int quantity, String supplierEmail) {}

    @depends Jdbc db
    @get("/items")
    public List<DbItem> list() {
        return db.query("SELECT * FROM db_items ORDER BY id", RowMapper.from(DbItem.class));
    }
}
```

`RowMapper.from(RecordClass)` matches SQL columns to record components by name,
including camelCase ↔ snake_case conversion — `supplier_email` maps to
`supplierEmail`, `created_at` to `createdAt`. This conversion fixes an issue
where multi-word columns were never matched and silently came back as `null`
(which then surfaced as a `NullPointerException` wherever the field was treated
as present), so a `supplier_email` column now maps reliably to a `supplierEmail`
record field.

## Testing

The `testkit` module provides an in-process `TestClient` — the real Netty pipeline,
no external processes:

```java
@Test
void itemsList() {
    try (TestClient client = TestClient.forApp(app)) {
        TestResponse res = client.get("/items?limit=10");
        assertEquals(200, res.status());
        assertTrue(res.text().contains("\"limit\":10"));
    }
}
```

Requests: `get/post/put/patch/delete/head/options`, JSON bodies, `postForm`,
`postMultipart`, raw `request(...)`. Responses expose `status()`, `ok()`, `text()`,
`json()` / `json(Class)`, `headers()`, `header(name)` (case-insensitive), `contentType()`.

## Interactive docs

With `javapi-openapi` on the classpath you get OpenAPI 3 JSON at `/openapi.json`
and rendered docs at `/docs` (Swagger UI) and `/redoc` (ReDoc) — parameter and
body schemas, validation constraints, and multipart upload shapes all included.

## CLI

The `javapi` CLI can run, hot-reload, and benchmark applications. See `javapi --help`.

## License

MIT.
