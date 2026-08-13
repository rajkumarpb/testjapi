# 3. Path, query, header & cookie parameters

javapi binds method parameters from the request by annotation and converts them
to the declared type automatically. This chapter covers every parameter source
except the request body (that's the next chapter).

> **What you'll learn**
>
> - `@Path`, `@Query`, `@Header`, `@Cookie` binding
> - Type conversion and how to make parameters optional
> - Injecting the raw `Request`

## The parameter table

| Annotation  | Source         | Example                                    |
|-------------|----------------|--------------------------------------------|
| `@Path`     | URL path segment | `@Get("/:itemId")` + `@Path int itemId`   |
| `@Query`    | Query string   | `@Query("q") String q`                     |
| `@Header`   | Request header | `@Header("user-agent") String ua`          |
| `@Cookie`   | Cookie         | `@Cookie("session") String session`        |

## Query parameters

```java
@Route("/search")
public class SearchController {

    @Get
    public Map<String, Object> search(
            @Query("q") String query,
            @Query("limit") @Optional Integer limit,
            @Query("sort") Optional<String> sort) {

        return Map.of(
                "query", query,
                "limit", limit == null ? 20 : limit,
                "sort", sort.orElse("name"));
    }
}
```

- `@Query("q")` names the query key explicitly. Without a value, javapi uses the
  parameter name (`@Query String q` is the same thing when compiled with
  `-parameters`).
- **Optional parameters** are declared either with `@Optional` (null when
  absent) or as `Optional<T>` (`Optional.empty()` when absent). Both work in
  parameters and record fields.
- Missing **required** parameters fail with `422` and a JSON error body.

## Type conversion

Scalar parameters (`@Path`, `@Query`, `@Header`, `@Cookie`, `@Form`, `@Value`)
are converted from their string form automatically:

- primitives and boxed numbers: `int`, `long`, `short`, `byte`, `double`,
  `float`, `boolean`, `char`
- `String`, `UUID`
- enums (by name, case-insensitive)
- `Optional<T>` and `@Optional` wrappers of all of the above

```java
@Get("/:itemId")
public Map<String, Object> item(
        @Path UUID itemId,
        @Query("include") @Optional boolean includeDetails) {
    return Map.of("itemId", itemId, "includeDetails", includeDetails);
}
```

An unparsable value (say `/items/not-a-uuid`) is a `422` with `loc` pointing at
the parameter. Dates and richer types belong in JSON bodies — see the next
chapter.

## Headers & cookies

```java
@Get("/whoami")
public Map<String, Object> whoami(
        @Header("user-agent") @Optional String userAgent,
        @Header("authorization") @Optional String authorization,
        @Cookie("session") @Optional String sessionId) {
    return Map.of(
            "userAgent", userAgent == null ? "" : userAgent,
            "authorized", authorization != null,
            "sessionId", sessionId == null ? "" : sessionId);
}
```

## Raw access with `Request`

Any handler can also accept a `Request` and read everything directly — useful
when a value doesn't map cleanly to a single parameter:

```java
@Get("/dump")
public Map<String, Object> dump(Request request) {
    return Map.of(
            "method", request.method(),
            "path", request.path(),
            "query", request.query(),
            "userAgent", request.header("user-agent"),
            "cookie", request.cookie("session"),
            "pathParam", request.pathParam("itemId"),
            "queryParams", request.queryParams());   // lazy-parsed Map
}
```

`Request` exposes `method()`, `uri()`, `path()`, `query()`, `pathParams()`,
`pathParam(name)`, `queryParams()`, `queryParam(name)`, `headers()`,
`header(name)`, `cookies()`, `cookie(name)`, `body()`, `form()`, `files()`,
and `file(name)`.

## Test it

```powershell
curl "http://localhost:8080/search?q=java&limit=5&sort=id"
curl "http://localhost:8080/items/00000000-0000-0000-0000-000000000001"
curl -H "user-agent: curl" --cookie "session=abc123" http://localhost:8080/whoami
```

Next up: [4. Request bodies](04-request-body.md) — records as JSON in and out.
