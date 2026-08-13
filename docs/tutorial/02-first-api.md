# 2. Your first API

In this chapter you'll organize endpoints into **controllers** and discover how
javapi wires them up from plain annotations.

> **What you'll learn**
>
> - Controller classes, `@route` prefixes, verb annotations
> - Path segments with `:name` and typed `@path` parameters
> - How `scan(...)` registers everything in one call
> - Programmatic routes for quick, one-off endpoints

## Controllers and routes

A controller is any class with a `@route` annotation and methods annotated with
a verb (`@get`, `@post`, `@put`, `@delete`, `@patch`). The class-level `@route`
is a path prefix; each method adds its own path.

```java
package com.example;

import java.util.List;
import java.util.Map;
import javapi.annotations.*;

@route("/items")
public class ItemController {

    @get("/")
    public List<String> list() {
        return List.of("alpha", "beta", "gamma");
    }

    @get("/:itemId")
    public Map<String, Object> item(@path int itemId) {
        return Map.of("itemId", itemId);
    }

    @post
    public Map<String, String> create() {
        return Map.of("created", "true");
    }
}
```

```java
import javapi.core.JavAPI;

public class Main {
    public static void main(String[] args) throws Exception {
        JavAPI.create()
                .scan("com.example")
                .start()
                .await();
    }
}
```

That's the whole registration story. `scan("com.example")` finds every class in
that package, reads the annotations, and wires up the routes:

- `GET /items` → `list()`
- `GET /items/:itemId` → `item(int itemId)` with `itemId` bound from the URL
- `POST /items` → `create()` (the verb annotation with no path defaults to `/`)

## Path segments

Named segments use `:name` in the route pattern and are bound with `@path`:

```java
@get("/:itemId")
public Map<String, Object> item(@path int itemId) { ... }
```

The bound value is converted to the parameter's type. `@path String itemId`,
`@path long id`, and `@path UUID id` all work — a bad value produces a `422`
with a JSON error body.

## Programmatic routes

Controller scanning is the main API, but you can also register lambdas directly
for small apps, health checks, or quick prototypes:

```java
JavAPI.create()
        .get("/health", request -> Map.of("status", "up"))
        .post("/echo", request -> request.body())
        .scan("com.example")
        .start()
        .await();
```

The `Handler` functional interface is `Object handle(Request request)` — return
any serializable value and it becomes the JSON response body.

## Test it

```powershell
curl http://localhost:8080/items
curl http://localhost:8080/items/42
curl -X POST http://localhost:8080/items
```

## Key ideas

- **Types drive everything.** The path, parameter types, and return type come
  from your Java declarations — there's no separate routing table to keep in
  sync.
- **Static paths win.** javapi keeps an exact-match index of parameterless
  routes, so `/items` is an O(1) lookup while `/:itemId` uses the pattern scan.
- **Order doesn't matter.** Static and parameterized routes coexist; javapi
  always prefers an exact static match over a pattern match for the same path.

Next up: [3. Path, query, header & cookie parameters](03-path-query-params.md).
