# 7. Dependency injection

javapi ships a small, zero-dependency DI container. Services are registered
once and pulled into any endpoint with `@Depends`.

> **What you'll learn**
>
> - `component(...)` for singletons and implementations
> - `requestScoped(...)` for per-request objects (DB sessions, request context)
> - `override(...)` for tests
> - `@Depends` parameters and `@Component` scanned classes
> - `@Value` for configuration injection

## Singletons

Register an instance, or an implementation class to be instantiated lazily:

```java
public interface Greeter {
    String greet(String name);
}

public class EnglishGreeter implements Greeter {
    @Override
    public String greet(String name) {
        return "Hello " + name;
    }
}
```

```java
JavAPI.create()
        .component(Greeter.class, new EnglishGreeter())      // instance
        .component(Greeter.class, EnglishGreeter.class)      // lazily instantiated
        .scan("com.example")
        .start();
```

Use it in any endpoint:

```java
import javapi.annotations.Depends;

@Get("/hello")
public Map<String, String> hello(@Depends Greeter greeter, @Query("name") String name) {
    return Map.of("greeting", greeter.greet(name));
}
```

`@Component`-annotated classes in a scanned package register themselves —
`EnglishGreeter` above could simply be:

```java
import javapi.annotations.Component;

@Component
public class EnglishGreeter implements Greeter { ... }
```

## Request-scoped components

A request-scoped component is created fresh for each request and closed when the
request ends. This is how DB connections and request context are modeled (see
[chapter 12](12-databases.md)):

```java
public record RequestContext(String userId) {}

JavAPI.create()
        .requestScoped(RequestContext.class, ctx -> new RequestContext(ctx.request().header("x-user-id")))
        .scan("com.example")
        .start();
```

The factory signature is `T create(DI.Context context)`; `context` exposes
`request()`, `resolve(type)`, and `close()`. Same code shape as:

```java
@Get("/me")
public Map<String, String> me(@Depends RequestContext requestContext) {
    return Map.of("userId", requestContext.userId());
}
```

## Overriding for tests

`override(...)` swaps a binding for the whole container — the idiomatic way to
fake a service in tests without touching production code:

```java
JavAPI app = JavAPI.create()
        .component(Greeter.class, EnglishGreeter.class)
        .override(Greeter.class, name -> "MOCK " + name)
        .scan("com.example");

try (TestClient client = TestClient.forApp(app)) {
    // every @Depends Greeter now receives the fake
}
```

## `@Value` — configuration in parameters and records

`@Value` injects a value from the configuration chain (properties, env vars,
system properties — see [chapter 11](11-configuration.md)):

```java
@Get("/info")
public Map<String, String> info(@Value("app.title") String title) {
    return Map.of("title", title);
}
```

With `application.properties`:

```properties
app.title=javapi demo
```

## How resolution works

1. `override(type, instance)` wins if present.
2. Registered bindings: instance → singleton factory (lazy) → request factory.
3. `@Component`-scanned classes register themselves as singletons.
4. `@Depends Connection` / `@Depends Jdbc` / `@Depends DataSource` resolve from
   the JDBC setup ([chapter 12](12-databases.md)).
5. Missing bindings fail fast with a message telling you to register or
   annotate the type.

Next up: [8. Middleware, CORS & static files](08-middleware-cors-static.md).
