# 4. Request bodies

In this chapter you'll declare request and response shapes as **records** and
let javapi parse, validate, and serialize them — with no code generation.

> **What you'll learn**
>
> - Binding a JSON body to a record with `@Body`
> - Nested records, lists, enums, dates, and UUIDs
> - Records as response types

## A record is your schema

Declare a record — that's the schema. javapi reflects over its components at
runtime:

```java
package com.example;

import java.util.List;
import java.util.UUID;
import javapi.annotations.*;

public record Item(
        UUID id,
        String name,
        int quantity,
        Money price,
        List<String> tags) {

    public record Money(long cents, String currency) {}
}
```

Use it as a request body and as a response:

```java
@Route("/items")
public class ItemController {

    private final List<Item> store = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Post
    public Item create(@Body Item item) {
        store.add(item);
        return item;
    }

    @Get
    public List<Item> list() {
        return store;
    }
}
```

```powershell
curl -X POST http://localhost:8080/items `
  -H "Content-Type: application/json" `
  -d '{"id":"0bf9b6c8-4a3c-4c0b-b4c4-3c8a2d9f1a20","name":"widget","quantity":3,"price":{"cents":1299,"currency":"USD"},"tags":["blue","sale"]}'
```

The response is the same record serialized back to JSON:

```json
{"id":"0bf9b6c8-...","name":"widget","quantity":3,"price":{"cents":1299,"currency":"USD"},"tags":["blue","sale"]}
```

## What converts automatically

In `@Body` records (and in nested values):

- primitives, boxed numbers, `String`, `boolean`, `BigDecimal`
- `UUID`, `java.time.LocalDate`, `LocalDateTime`, `Instant` (ISO-8601)
- enums (by name)
- other records (nested objects)
- `List<T>`, `Set<T>`, `Map<String, T>`
- `Optional<T>` components and `@Optional` fields (null when missing)
- `byte[]` (base64)

Unknown or mistyped values fail with `422` and a JSON error body naming the
field (see [chapter 5](05-validation.md) for the exact shape).

## Empty and unknown fields

- A missing `@Optional`/`Optional` component is fine; a missing required one is
  a `422`.
- `null` in the body for a non-optional primitive (`int quantity`) is also a
  `422`.
- Extra fields the record doesn't declare are ignored by default.

## Validation on the type itself

Constraints live right on the record components, so every endpoint that uses
the record gets them for free:

```java
public record Item(
        @MinLength(2) @MaxLength(40) String name,
        @Min(0) @Max(100000) int quantity,
        @Email @Optional String supplierEmail) {
}
```

That's the subject of the next chapter.

Next up: [5. Validation](05-validation.md).
