# 13. Interactive OpenAPI docs

Add the `openapi` module and every endpoint gets an OpenAPI 3 document and two
interactive UIs — generated from your types, not maintained by hand.

> **What you'll learn**
>
> - What the `openapi` module adds
> - Where the schemas come from
> - The three endpoints (`/openapi.json`, `/docs`, `/redoc`)

## Enable it

```groovy
dependencies {
    implementation "dev.javapi:javapi-openapi:0.1.0"
}
```

That's it. `start()` (and the `TestClient`) discovers the module via
`ServiceLoader` and installs the docs routes automatically:

| Endpoint        | What you get                          |
|-----------------|---------------------------------------|
| `/openapi.json` | the raw OpenAPI 3 document (JSON)     |
| `/docs`         | Swagger UI — try endpoints in browser |
| `/redoc`        | ReDoc — reference documentation       |

```powershell
curl http://localhost:8080/openapi.json
```

## Where the schemas come from

Everything is derived from your declarations:

- **Paths** from `@route` prefixes + verb-annotation paths.
- **Parameters** from `@path` / `@query` / `@header` / `@cookie` with their
  names, types, and `optional` markers.
- **Bodies** from `@body` records — the component tree becomes a JSON schema.
- **Constraints** (`@min`, `@max`, `@minlength`, `@maxlength`, `@pattern`,
  `@email`) are embedded as schema keywords, so Swagger UI shows them and can
  validate client-side.
- **Multipart** uploads appear as `multipart/form-data` with the file parts.

So a controller like this:

```java
@route("/items")
public class ItemController {

    public record Item(
            @minlength(2) @maxlength(40) String name,
            @min(0) int quantity,
            @email @optional String supplierEmail) {
    }

    @post
    public Item create(@body Item item) {
        return item;
    }
}
```

produces an `/items` POST path whose body schema carries the
`minLength` / `maxLength` / `minimum` / `format: email` keywords from the
annotations — with no OpenAPI code anywhere in your project.

## Swagger UI

Open `http://localhost:8080/docs`:

1. Every route is listed with its method, path, parameters, and schema.
2. **Try it out** fills a request form from the schema and shows the JSON
   response.
3. The `422` responses for your validation constraints are documented too.

## ReDoc

`http://localhost:8080/redoc` renders the same document as a scrollable,
three-panel reference — handy for sharing with frontend/API consumers.

Next up: [14. Testing](14-testing.md).
