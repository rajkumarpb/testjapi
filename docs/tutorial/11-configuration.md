# 11. Configuration

javapi reads configuration from a cascade of sources with relaxed binding — the
same value can come from code, system properties, environment variables, or a
properties file.

> **What you'll learn**
>
> - `application.properties` and `application-{profile}.properties`
> - The precedence chain (what wins)
> - `server.*` settings and relaxed binding
> - Injecting values with `@Value`

## Property files

Drop `application.properties` on the classpath (`src/main/resources`):

```properties
server.port=8000
server.workers=8
app.title=javapi demo
app.workers=4
```

A profile overlay, `application-{profile}.properties`, layers on top. Activate
it with `-Djavapi.profile=prod` or the `JAVAPI_PROFILE` environment variable:

```powershell
java -Djavapi.profile=prod -jar app.jar
$env:JAVAPI_PROFILE="prod"; java -jar app.jar
```

## Precedence (first hit wins)

| Rank | Source                                            |
|------|---------------------------------------------------|
| 1    | Code (`ServerSettings` set via `app.port(...)`, ...) |
| 2    | System properties (`-Djavapi.port=9000`)          |
| 3    | Environment variables (`JAVAPI_PORT=9000`)        |
| 4    | Profile file (`application-prod.properties`)      |
| 5    | Base file (`application.properties`)              |
| 6    | Built-in defaults (`8080`, workers = CPU count, ...) |

So `JAVAPI_PORT=9000` overrides `server.port` from the file, unless the code
called `.port(...)` explicitly — code config always wins.

## Relaxed binding

Keys are normalized, so one logical setting has several spellings:

- File key: `server.port`
- System property: `-Djavapi.port=9000` (the `javapi.` prefix; `server.` is
  shortened, so `-Djavapi.port` and `-Djavapi.workers` work)
- Env var: `JAVAPI_PORT`, `JAVAPI_WORKERS` (dots → underscores, uppercased)

## Server settings

| Key                     | Meaning                            |
|-------------------------|------------------------------------|
| `server.host`           | bind address (default `localhost`) |
| `server.port`           | port (default `8080`)              |
| `server.workers`        | worker pool size                   |
| `server.eventLoopInline`| dispatch `@EventLoop` routes on the event loop |
| `server.logRequests`    | per-request logging                |

Each also has a fluent setter (`app.port(8000)`) that takes precedence over all
other sources:

```java
JavAPI.create()
        .port(8000)
        .logRequests(true)
        .scan("com.example")
        .start();
```

## Reading config in code

Inject values into parameters and record components with `@Value`:

```java
import javapi.annotations.Value;

@Get("/info")
public Map<String, String> info(
        @Value("app.title") String title,
        @Value("app.workers") @Optional Integer workers) {
    return Map.of("title", title, "workers", workers == null ? "n/a" : workers.toString());
}
```

A `@Value` with no matching value is a missing-parameter error (`422`-style);
mark it `@Optional` or use `Optional<Integer>` when a default should be empty.

Or read the `Config` object directly:

```java
import javapi.config.Config;

Config config = Config.load();
String title = config.get("app.title", "untitled");
int port = config.getInt("server.port", 8080);
boolean logging = config.getBoolean("server.logRequests", false);
```

`Config` offers `get`, `getInt`, `getBoolean`, and `with(key, value)`, all using
the same relaxed chain.

Next up: [12. Databases with JDBC](12-databases.md).
