# javapi documentation

Welcome! javapi is a FastAPI-inspired, type-driven REST framework for Java 21.
The [README](../README.md) is a quickstart — a single page that shows off every
feature. This documentation goes deeper: a **tutorial** that builds one API from
an empty directory to a deployed service, chapter by chapter.

## Tutorial (learn by building)

The tutorial follows the same arc as FastAPI's user guide. Each chapter is short,
self-contained, and builds on the previous one. Every snippet is copy-pasteable.

1. [Installation & first project](tutorial/01-installation.md) — Java 21, Gradle, dependencies, hello world.
2. [Your first API](tutorial/02-first-api.md) — controllers, `@route`, verb annotations, JSON.
3. [Path, query, header & cookie parameters](tutorial/03-path-query-params.md) — typed binding from the request.
4. [Request bodies](tutorial/04-request-body.md) — records as JSON bodies and responses.
5. [Validation](tutorial/05-validation.md) — constraints, `422` errors, custom messages.
6. [Responses & error handling](tutorial/06-responses-errors.md) — `Response`, `HttpException`, `@exception`.
7. [Dependency injection](tutorial/07-dependency-injection.md) — components, request scopes, `@depends`.
8. [Middleware, CORS & static files](tutorial/08-middleware-cors-static.md) — hooks around every request.
9. [File uploads & WebSockets](tutorial/09-uploads-websockets.md) — multipart forms and real-time sockets.
10. [Streaming (SSE) & async handlers](tutorial/10-streaming-sse.md) — `SseEmitter`, `@eventloop`, futures.
11. [Configuration](tutorial/11-configuration.md) — properties files, profiles, env vars, `@value`.
12. [Databases with JDBC](tutorial/12-databases.md) — `Jdbc`, record row-mapping, transactions.
13. [Interactive OpenAPI docs](tutorial/13-openapi-docs.md) — `/docs`, `/redoc`, `/openapi.json`.
14. [Testing](tutorial/14-testing.md) — in-process `TestClient` with JUnit.
15. [CLI & deployment](tutorial/15-cli-deploy.md) — hot reload, benchmarks, jlink images, GraalVM native.

## Reference

- The [README](../README.md) doubles as the feature reference — annotations table,
  validation table, and every built-in API in one page.
- See the [examples module](../examples/src/main/java/demo/) for a complete running
  application (routes, DI, CORS, static files, WebSockets, SSE, and an H2-backed JDBC
  controller) you can run with `gradle :examples:run`.
