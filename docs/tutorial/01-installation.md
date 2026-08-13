# 1. Installation & first project

In this chapter you'll set up a Java 21 project with Gradle, add javapi, and
run a one-endpoint API.

> **What you'll learn**
>
> - Which artifacts to depend on
> - Why the `-parameters` compiler flag matters
> - The minimal `main` method
> - How to run the app and open the interactive docs

## Requirements

- **JDK 21** or newer. javapi uses records, pattern matching, and virtual
  threads — all final in 21. Check with `java -version`.
- **Gradle 8.x/9.x** or Maven (or just `javac` — the `javapi` CLI can compile
  for you, see [chapter 15](15-cli-deploy.md)).

javapi is split into small modules so a project only pulls in what it uses:

| Artifact                         | Purpose                                   |
|----------------------------------|-------------------------------------------|
| `javapi-core`                    | routing, parameters, validation, JSON, DI |
| `javapi-server`                  | the Netty HTTP transport                   |
| `javapi-openapi`                 | `/docs`, `/redoc`, `/openapi.json`         |
| `javapi-testkit`                 | in-process `TestClient` for tests          |
| `javapi-jdbc-pool`               | optional HikariCP connection pooling       |
| `javapi-config-yaml` / `javapi-json-jackson` | optional adapters                 |

For a normal web API you always need `core` **and** `server`. `core` alone has
no transport; `start()` fails fast if it can't find a `ServerFactory` on the
classpath.

## Create the project

### With Gradle

`build.gradle.kts`:

```kotlin
plugins {
    java
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.javapi:javapi-core:0.1.0")
    implementation("dev.javapi:javapi-server:0.1.0")
    implementation("dev.javapi:javapi-openapi:0.1.0")     // /docs, /redoc
    testImplementation("dev.javapi:javapi-testkit:0.1.0")  // TestClient
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass = "com.example.Main"
}

// javapi binds method parameters by name — the compiler must keep them.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}
```

> **The `-parameters` flag is not optional.** javapi reads parameter *names*
> from the bytecode (there is no code generation and no `@Param("name")`
> plumbing). Without `-parameters`, every parameter shows up as `arg0`, `arg1`,
> and you'll get 422s for unmatched names. All Gradle setups here include it.

The Gradle Groovy DSL equivalent:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ['-parameters']
}
```

## Hello world

`src/main/java/com/example/Main.java`:

```java
package com.example;

import java.util.Map;
import javapi.core.JavAPI;

public class Main {
    public static void main(String[] args) throws Exception {
        JavAPI.create()
                .get("/", request -> Map.of("Hello", "World"))
                .start()
                .await();
    }
}
```

## Run it

```powershell
./gradlew run
```

Then in another terminal:

```powershell
curl http://localhost:8080/
```

```json
{"Hello":"World"}
```

Open <http://localhost:8080/docs> — because `javapi-openapi` is on the
classpath you already have interactive Swagger UI, plus ReDoc at
`/redoc` and the raw OpenAPI document at `/openapi.json`.

Press `Ctrl+C` to stop. `await()` blocks until the server shuts down cleanly.

## What just happened

- `.get("/", handler)` registered one route programmatically — no controller
  class needed.
- The handler takes a `Request` and returns a `Map`, which javapi serializes
  to JSON automatically.
- `.start()` resolved the transport via `ServiceLoader` (the `server` module),
  loaded configuration, and bound `localhost:8080`.

Next up: [2. Your first API](02-first-api.md) — controllers and annotations.
