# 8. Middleware, CORS & static files

Middleware wraps every request in a chain you control — logging, auth, rate
limits, request IDs. CORS and static files are built-in middleware you can
enable in one line.

> **What you'll learn**
>
> - The `Middleware` / `Next` contract
> - `app.use(...)` and `@Middleware` classes
> - Built-in `cors(...)` and `staticFiles(...)`

## The contract

`Middleware` is a functional interface: `Object handle(Request request, Next next)`.
Call `next.next(request)` to continue down the chain (which eventually reaches
the matched route), or return a value to short-circuit.

```java
import javapi.middleware.Middleware;
import javapi.middleware.Next;
import javapi.request.Request;

JavAPI.create()
        .use((Request request, Next next) -> {
            long start = System.nanoTime();
            Object result = next.next(request);
            System.out.printf("%s %s -> %s in %d ns%n",
                    request.method(), request.path(), result, System.nanoTime() - start);
            return result;
        })
        .scan("com.example")
        .start();
```

Middleware registered first runs first (outermost). You can:

- **Short-circuit** — return early without calling `next`:

  ```java
  .use((request, next) -> {
      if (!"secret".equals(request.header("x-api-key"))) {
          return Response.of(401, Map.of("detail", "Missing or bad API key"));
      }
      return next.next(request);
  })
  ```

- **Rewrite the result** — wrap or modify what came back from `next`:

  ```java
  .use((request, next) -> {
      Object result = next.next(request);
      return result instanceof Response r ? r.withHeader("X-Powered-By", "javapi") : result;
  })
  ```

## `@Middleware` classes

Middleware classes in a scanned package register automatically. A class is a
valid `@Middleware` provider if it **implements `Middleware`** or **exposes a
public method returning one**:

```java
import javapi.annotations.Middleware;
import javapi.middleware.Middleware;
import javapi.middleware.Next;
import javapi.request.Request;

@Middleware
public class RequestIdMiddleware implements Middleware {
    @Override
    public Object handle(Request request, Next next) {
        String id = request.header("x-request-id");
        Object result = next.next(request);
        return result instanceof Response r && id != null
                ? r.withHeader("X-Request-Id", id)
                : result;
    }
}
```

## CORS

```java
import javapi.middleware.Cors;

JavAPI.create()
        .cors(Cors.config().origins("http://localhost:5173"))
        .scan("com.example")
        .start();
```

`Cors.config()` defaults to `*` origins, the standard methods, `*` headers.
Fluent overrides:

```java
Cors.config()
        .origins("https://app.example.com", "https://staging.example.com")
        .methods("GET", "POST", "PUT", "DELETE")
        .headers("Authorization", "Content-Type")
        .credentials(true)
```

Preflight (`OPTIONS` with `Access-Control-Request-Method`) is answered with
`204` plus `Access-Control-Allow-Methods` / `Access-Control-Max-Age` /
`Access-Control-Allow-Headers`. When credentials are enabled the origin is
echoed instead of `*`.

## Static files

From the **classpath** (serves `src/main/resources/static/...`):

```java
.staticFiles("/static")
```

From a **directory** on disk:

```java
import java.nio.file.Path;

.staticFiles("/uploads", Path.of("./uploads"))
```

- `index.html` is served for the bare prefix; a missing file falls through to
  the next middleware/route instead of erroring.
- Path traversal (`..`) is rejected.
- Content types are set from the file extension (html, css, js, images, ...).

## Test it

```powershell
curl http://localhost:8080/static/index.html
curl -H "Origin: http://localhost:5173" -i http://localhost:8080/items
```

Next up: [9. File uploads & WebSockets](09-uploads-websockets.md).
