# 12. Databases with JDBC

javapi's JDBC support keeps `java.sql` (JDK-only) in `core`: a request-scoped
`Jdbc` helper, record row-mapping, and transactions. Connection pooling is an
optional adapter module.

> **What you'll learn**
>
> - Enabling JDBC with `app.jdbc(url, user, pass)` or `db.url` config
> - `@Depends Jdbc` and record row-mapping with `RowMapper.from(...)`
> - `insert` / `update` / `findOne` / `query`
> - Transactions with `@Transaction` and `Jdbc.tx`
> - How `jdbc-pool` plugs in automatically

## Setup

```java
JavAPI.create()
        .jdbc("jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1", "sa", "")
        .scan("com.example")
        .start();
```

Or configure through the config chain ([chapter 11](11-configuration.md)):

```properties
db.url=jdbc:postgresql://localhost/app
db.user=app
db.password=secret
db.pool.max=20
```

When `db.url` is present in config and no datasource was registered in code,
`start()` wires the JDBC setup automatically.

Each request gets its **own** `Connection` (request-scoped, not thread-local —
safe with virtual threads), committed/closed at the end of the request.

## Record row-mapping

Define a record matching the columns, then map rows straight into it:

```java
public record DbItem(long id, String name, int quantity, String supplierEmail) {}
```

```java
import javapi.annotations.Depends;
import javapi.annotations.Get;
import javapi.annotations.Path;
import javapi.jdbc.Jdbc;
import javapi.jdbc.RowMapper;
import javapi.request.HttpException;

@Route("/db")
public class DbController {

    @Get("/items")
    public List<DbItem> list(@Depends Jdbc db) {
        return db.query("SELECT * FROM db_items ORDER BY id", RowMapper.from(DbItem.class));
    }

    @Get("/items/:id")
    public DbItem item(@Depends Jdbc db, @Path long id) {
        return db.findOne("SELECT * FROM db_items WHERE id = ?", RowMapper.from(DbItem.class), id)
                .orElseThrow(() -> new HttpException(404, "Item not found"));
    }
}
```

`RowMapper.from(record)` matches **column labels to record component names** by
name, including camelCase ↔ snake_case conversion — `supplier_email` maps to
`supplierEmail`. Types convert automatically: numbers, `String`, booleans,
`UUID`, `LocalDate` / `LocalDateTime`, enums, and `Optional<T>` components.

## Writes and generated keys

```java
import javapi.annotations.Body;
import javapi.annotations.Delete;
import javapi.annotations.Post;

public record CreateItem(String name, int quantity, String supplierEmail) {}

@Post("/items")
public Map<String, Object> create(@Depends Jdbc db, @Body CreateItem input) {
    long id = db.insert(
            "INSERT INTO db_items(name, quantity, supplier_email) VALUES (?,?,?)",
            input.name(), input.quantity(), input.supplierEmail());
    return Map.of("id", id);
}

@Get("/items/:id")
public Map<String, Object> get(@Depends Jdbc db, @Path long id) {
    return db.findOne("SELECT * FROM db_items WHERE id = ?", RowMapper.from(DbItem.class), id)
            .orElseThrow(() -> new HttpException(404, "Item not found"));
}

@Delete("/items/:id")
public Map<String, Object> delete(@Depends Jdbc db, @Path long id) {
    int removed = db.update("DELETE FROM db_items WHERE id = ?", id);
    return Map.of("removed", removed);
}
```

The `Jdbc` helper:

| Method                              | Returns                  |
|-------------------------------------|--------------------------|
| `query(sql, mapper, params...)`     | `List<T>`                |
| `findOne(sql, mapper, params...)`   | `Optional<T>`            |
| `update(sql, params...)`            | affected row count       |
| `insert(sql, params...)`            | generated key (`long`)   |
| `tx(block)`                         | block result             |
| `connection()`                      | the raw `Connection`     |

`Optional` and `@Optional` values in `params` become SQL `NULL` when empty.

## Transactions

Either annotate the endpoint — the transaction opens on the first JDBC call,
commits on success, rolls back on any exception:

```java
import javapi.annotations.Transaction;
import javapi.annotations.Query;

@Post("/transfer")
@Transaction
public Map<String, Object> transfer(@Depends Jdbc db,
        @Query("from") long from, @Query("to") long to, @Query("amount") int amount) {
    int moved = db.update("UPDATE db_items SET quantity = quantity - ? WHERE id = ?", amount, from);
    if (moved != 1) {
        throw new HttpException(404, "Source item not found");
    }
    int credited = db.update("UPDATE db_items SET quantity = quantity + ? WHERE id = ?", amount, to);
    if (credited != 1) {
        throw new HttpException(404, "Destination item not found");
    }
    return Map.of("ok", true);
}
```

Or wrap a block of work explicitly:

```java
@Post("/import")
public Map<String, Object> importAll(@Depends Jdbc db, @Body List<CreateItem> items) throws Exception {
    return Jdbc.tx(db, () -> {
        int total = 0;
        for (CreateItem item : items) {
            db.insert("INSERT INTO db_items(name, quantity, supplier_email) VALUES (?,?,?)",
                    item.name(), item.quantity(), item.supplierEmail());
            total++;
        }
        return Map.of("inserted", total);
    });
}
```

## Connection pooling (optional)

Add `javapi-jdbc-pool` to the classpath and the HikariCP-backed pool is picked
up automatically via `ServiceLoader` — no code changes:

```groovy
dependencies {
    implementation "dev.javapi:javapi-core:0.1.0"
    implementation "dev.javapi:javapi-server:0.1.0"
    implementation "dev.javapi:javapi-jdbc-pool:0.1.0"
}
```

Pool tuning via the config chain:

```properties
db.pool.max=20
db.pool.min=2
db.pool.name=app-pool
db.pool.timeout=30000
```

## Errors

`SQLException`s are mapped for you: connection errors (SQLState `08*`) become
`503 {"detail":"Database unavailable"}`; anything else becomes
`500 {"detail":"Database error"}` — no raw stack traces or driver internals in
the JSON body. Logs still carry the detail for debugging.

Next up: [13. Interactive OpenAPI docs](13-openapi-docs.md).
