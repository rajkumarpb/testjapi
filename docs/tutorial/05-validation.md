# 5. Validation

javapi validates parameters and record fields against constraint annotations
and answers with `422` when something doesn't fit.

> **What you'll learn**
>
> - The constraint annotations
> - The `422` error body shape
> - Validation on parameters, record fields, and nested records

## The constraints

| Annotation       | Meaning                    | Applies to        |
|------------------|----------------------------|-------------------|
| `@Min` / `@Max`  | numeric bounds (inclusive) | numbers           |
| `@MinLength` / `@MaxLength` | string length bounds | strings    |
| `@Pattern`       | regular expression         | strings           |
| `@Email`         | email format               | strings           |
| `@Optional`      | nullable / optional        | anything          |

They work on **method parameters** (all sources) and on **record components**
(request bodies and responses):

```java
@Get("/search")
public Map<String, Object> search(
        @Query("q") @MinLength(2) @MaxLength(50) String q,
        @Query("limit") @Min(1) @Max(100) int limit) {
    return Map.of("q", q, "limit", limit);
}
```

```java
public record Item(
        @MinLength(2) @MaxLength(40) String name,
        @Min(0) @Max(100000) int quantity,
        @Email @Optional String supplierEmail) {
}
```

Validation is recursive: constraints on nested records run when they're parsed,
so a `price` record with a `@Min(0)` cents component is checked as part of the
parent body.

## The 422 error body

A failed validation returns HTTP `422` with a FastAPI-style body. Each problem
is an object with a `loc` (path to the offending value), a human-readable
`msg`, and a machine-readable `type`:

```json
{
  "detail": [
    {"loc": ["body", "name"], "msg": "length must be between 2 and 40", "type": "length"},
    {"loc": ["query", "limit"], "msg": "value 0 is below min 1", "type": "min"}
  ]
}
```

- `loc` uses `"body"`, `"query"`, `"path"`, `"header"`, `"cookie"`, `"form"`,
  `"file"`, or `"config"` (for `@Value`) as the source, then the field path
  (`["body","price","cents"]` for a nested component).
- The response `Content-Type` is `application/json`.

## Type conversion errors are validation errors too

A path segment that can't become an `int`, a UUID that isn't a UUID, an unknown
enum name — all produce a `422` with `loc` pointing at the parameter:

```json
{"detail": [{"loc": ["path", "itemId"], "msg": "invalid int", "type": "int"}]}
```

## Customizing failures

If you need a different response shape for validation errors, register a handler
for the validation exception type (see [chapter 6](06-responses-errors.md)):

```java
import javapi.params.RequestValidationError;

JavAPI.create()
        .exception(RequestValidationError.class, error ->
                Response.of(422, Map.of("errors", error.errors())))
        .scan("com.example")
        .start();
```

`RequestValidationError` exposes `errors()` returning the list of
`{loc, msg, type}` entries.

Next up: [6. Responses & error handling](06-responses-errors.md).
