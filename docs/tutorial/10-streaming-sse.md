# 10. Streaming (SSE) & async handlers

Server-Sent Events push a stream of updates down one HTTP connection. This
chapter also covers the two ways to control *where* your handler runs.

> **What you'll learn**
>
> - Returning an `SseEmitter` from a route
> - `@eventloop` vs `@blocking` execution modes
> - Returning `CompletableFuture` for genuinely async handlers

## Server-Sent Events

Return an `SseEmitter` and push events with `send`, `event`, or `comment`:

```java
import javapi.sse.SseEmitter;

@get("/ticker")
public SseEmitter ticker() {
    SseEmitter emitter = SseEmitter.create();
    emitter.event("tick", "1");              // event: tick / data: 1
    emitter.send("hello");                   // data: hello
    emitter.send(Map.of("n", 2));            // data: <json>
    emitter.comment("keepalive");            // : keepalive
    emitter.complete();
    return emitter;
}
```

The emitter buffers events until the response attaches, then flushes them; a
handler can also keep the connection open and emit from another thread:

```java
@get("/clock")
public SseEmitter clock() {
    SseEmitter emitter = SseEmitter.create();
    var timer = new java.util.concurrent.ScheduledThreadPoolExecutor(1);
    timer.scheduleAtFixedRate(
            () -> emitter.send(Map.of("time", java.time.LocalTime.now().toString())),
            0, 1, java.util.concurrent.TimeUnit.SECONDS);
    return emitter;
}
```

The emitter stays open until `complete()` is called or the client disconnects
(no reconnect/stuck timer in the response path — the framework releases the
connection on close). The response is `text/event-stream` with
`X-Accel-Buffering: no`, so nginx-style buffering never delays events.

```powershell
curl -N http://localhost:8080/ticker
```

## Where your handler runs

Routes dispatch to a **virtual thread per request** by default, so blocking code
(JDBC, `Thread.sleep`, HTTP calls) is fine — a slow endpoint never stalls the
server. You can opt out with two annotations:

```java
import javapi.annotations.eventloop;
import javapi.annotations.blocking;

@get("/inline")
@eventloop
public Map<String, String> inline() {
    return Map.of("mode", "eventloop");   // runs on the Netty event loop — never block!
}

@get("/cpu-heavy")
@blocking
public Map<String, String> heavy() {
    return Map.of("mode", "blocking");    // forced off the event loop
}
```

Rules of thumb:

- **`@eventloop`** — pure CPU, no IO, no locking. Fastest for hot endpoints;
  the benchmark app sets `eventLoopInline(true)` so `@eventloop` routes skip the
  thread handoff entirely.
- **`@blocking`** — explicit, in case automatic detection isn't what you want.
- **Default** — blocking-safe via virtual threads. Never touch JDBC or blocking
  IO from an `@eventloop` handler.

## Returning futures

Handlers may also return a `CompletableFuture` / `CompletionStage` / `Future`;
javapi awaits it and serializes the result:

```java
@get("/future")
public CompletableFuture<Map<String, String>> future() {
    return CompletableFuture.supplyAsync(() -> Map.of("async", "true"));
}
```

`CompletionException` / `ExecutionException` are unwrapped and routed through
the normal exception mapper.

Next up: [11. Configuration](11-configuration.md).
