# 15. CLI & deployment

The `javapi` CLI runs, hot-reloads, benchmarks, and packages your app — including
a jlink-trimmed runtime and a GraalVM native image. This chapter walks the
whole path from dev loop to production.

> **What you'll learn**
>
> - `javapi run` and `javapi dev` (hot reload)
> - `javapi bench` (throughput + cold start)
> - `javapi jar` (AppCDS + jlink runtime)
> - `javapi native` (GraalVM native image)
> - Deployment notes

## The CLI

```text
javapi run <mainClass> [--cp <classpath>] [--jvm <arg>]...
javapi dev <mainClass> [--cp <classpath>] [--src <dir>] [--jvm <arg>]...
javapi bench [--url <url>] [--requests N] [--concurrency C]
javapi bench --app <mainClass> [--cp <classpath>] [--port P]
javapi jar <mainClass> [--cp <classpath>] [--jvm <arg>]...
javapi native <mainClass> [--cp <classpath>] [--port P] [--urls "/p1,/p2"] [--o <name>]
javapi version
```

### Run

```powershell
javapi run demo.Main --cp "build/classes/java/main;build/resources/main"
```

Spawns a JVM with your main class and the given classpath.

### Dev (hot reload)

```powershell
javapi dev demo.Main --src src --cp "build/classes/java/main;build/resources/main"
```

Compiles `src` with `-parameters`, starts the app, and watches the directory. On
any `.java` change it recompiles and restarts the server — no manual restarts
while iterating.

## Benchmarks

Against a running server:

```powershell
javapi bench --url http://localhost:8080/ --requests 30000 --concurrency 32
```

Launch-then-benchmark (also prints **cold start**, time-to-first-request):

```powershell
javapi bench --app demo.Main --port 8080 --requests 30000 --concurrency 32
```

Output includes throughput (req/s), failures, and p50/p95/p99 latency.

## Package: `javapi jar` (jlink + AppCDS)

```powershell
javapi jar demo.Main --cp "..."
```

Builds under `build/javapi-image/`:

1. An **AppCDS archive** (`-XX:ArchiveClassesAtExit` while the app boots).
2. A **jlink-trimmed runtime** containing only the JDK modules you use.
3. Launcher scripts: `build\javapi-image\run.bat` (Windows) or
   `build/javapi-image/run` (Linux/macOS).

Run the trimmed image:

```powershell
.\build\javapi-image\run.bat
```

This is the low-friction path to a small, fast-starting production runtime
without changing your JDK setup.

## Package: `javapi native` (GraalVM)

Requires a GraalVM JDK with `native-image` (set `GRAALVM_HOME` or put
`native-image` on `PATH`). The command:

```powershell
javapi native demo.Main --cp "..." --port 8080 --urls "/,/db/items,/items"
```

It works in three stages:

1. **Capture** — runs your app under the GraalVM **tracing agent** and
   exercises the routes in `--urls` (list every route that touches a DB, pool,
   or record type so their reflection metadata is captured).
2. **Check** — aborts with a clear message if no reflection config was
   captured (fail-closed: it never builds a silently broken image).
3. **Build** — invokes `native-image` with the captured config and
   `--no-fallback`, producing a standalone executable.

Run the image — the CLI prints the executable's path when the build finishes
(`demo.exe` on Windows, `demo` on Linux/macOS):

```powershell
.\demo.exe --port 8080
```

On HotSpot (not GraalVM) the command fails fast within ~3 s with an
install-GraalVM message instead of hanging.

## Deployment notes

- **Runtime profile:** set `JAVAPI_PROFILE=prod` (or `-Djavapi.profile=prod`)
  so your `application-prod.properties` overrides dev defaults.
- **Bind address:** `server.host` defaults to `localhost` — set `JAVAPI_HOST=0.0.0.0`
  (or `.host("0.0.0.0")`) so the app is reachable outside the box.
- **Reverse proxy:** put nginx/Caddy in front for TLS and routing; SSE responses
  already set `X-Accel-Buffering: no` so nginx won't buffer events.
- **Container:** the jlink image is a natural Docker base — copy `run`/`run.bat`
  and the runtime into a slim image. A minimal `Dockerfile` sketch:

  ```dockerfile
  FROM eclipse-temurin:21 AS build
  COPY . /src
  WORKDIR /src
  RUN ./gradlew -q installDist && javapi jar demo.Main --cp "..."
  FROM alpine:latest
  COPY --from=build /src/build/javapi-image/ /app
  ENV JAVAPI_PORT=8080 JAVAPI_HOST=0.0.0.0
  EXPOSE 8080
  CMD ["/app/run"]
  ```

- **Config in production** comes from environment variables — no secrets in
  files: `JAVAPI_PORT`, `JAVAPI_HOST`, `DB_URL`, `DB_USER`, `DB_PASSWORD`.

## Where to go from here

- The [README](../../README.md) is the full feature reference.
- The [examples module](../../examples/src/main/java/demo/) runs end-to-end:
  `gradle :examples:run` then open <http://localhost:8000/docs>.

That's the end of the tutorial — you now have a typed, validated, documented,
tested API from an empty directory to a deployed runtime.
