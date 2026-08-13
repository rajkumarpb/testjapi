# 6. Responses & error handling

Any serializable return value becomes a JSON response. When you need control —
status codes, headers, errors — use `Response`, `HttpException`, and exception
handlers.

> **What you'll learn**
>
> - `Response` for status codes, bodies, and headers
> - `HttpException` for JSON errors with any status
> - Mapping exceptions with `@ExceptionHandler` methods and `app.exception(...)`
> - Background tasks after the response is sent

## Returning plain values

Return records, enums, dates, `UUID`, lists, `Map`, `byte[]`... javapi
serializes it as JSON:

```java
@Get("/ping")
public Map<String, Object> ping() {
    return Map.of("pong", true, "time", java.time.Instant.now());
}
```

## Full control with `Response`

```java
import javapi.request.Response;

@Post("/items")
public Response create(@Body Item item) {
    return Response.of(201, item)
            .withHeader("Location", "/items/" + item.id());
}
```

The `Response` API:

```java
Response.ok(body)                              // 200
Response.status(204)                           // 204, no body
Response.of(status, body)                      // any status + JSON body
response.withStatus(status)
response.withBody(body)
response.withHeader(name, value)
response.withBackgroundTasks(tasks)
```

`Response.of(200, byte[])` writes the bytes verbatim (no JSON wrapping), which
is how static files and binary payloads are served.

## Raising errors

Throw `HttpException(status, detail)` — the response is JSON with that status
and a `detail` field:

```java
import javapi.request.HttpException;

@Get("/items/:itemId")
public Item item(@Path int itemId) {
    if (itemId <= 0) {
        throw new HttpException(404, "Item not found");
    }
    if (!isAllowed()) {
        throw new HttpException(403, "Forbidden");
    }
    return item;
}
```

```json
{"detail": "Item not found"}
```

`detail` can be any serializable value, not just a string.

## Mapping other exceptions

Any uncaught exception becomes a `500` with `{"detail": <message>}`. To control
the shape (or status) for specific exception types, register a handler:

**Programmatically:**

```java
JavAPI.create()
        .exception(IllegalStateException.class, error ->
                Response.of(500, Map.of("detail", error.getMessage(), "error", "illegal_state")))
        .scan("com.example")
        .start();
```

**Or as a controller method** — the handler must accept exactly one parameter,
the exception, and return a `Response`:

```java
@Route("/items")
public class ItemController {

    // ... routes ...

    @ExceptionHandler(IllegalArgumentException.class)
    public Response onBadInput(IllegalArgumentException error) {
        return Response.of(400, Map.of("detail", error.getMessage(), "error", "bad_input"));
    }

    @ExceptionHandler(Exception.class)
    public Response onAny(Throwable error) {
        return Response.of(500, Map.of("detail", "Something went wrong"));
    }
}
```

Handlers are matched by most-specific type first (walking superclasses then
interfaces), so a `@ExceptionHandler(IllegalArgumentException.class)` handler wins over
`@ExceptionHandler(Exception.class)` for an `IllegalArgumentException`.

## Background tasks

Work that should run *after* the response is sent — email, metrics, cache
warm-up — goes into `BackgroundTasks`:

```java
import javapi.request.BackgroundTasks;

@Post("/items")
public Response create(@Body Item item) {
    return Response.of(201, item)
            .withBackgroundTasks(BackgroundTasks.of(() -> notifySubscribers(item)));
}
```

Tasks run after the response is flushed; exceptions inside them are swallowed so
a background failure never breaks the response.

Next up: [7. Dependency injection](07-dependency-injection.md).
