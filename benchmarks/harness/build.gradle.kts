plugins {
    id("javapi.java-conventions")
    application
}

description = "Comparative benchmark harness (javapi vs Javalin vs Jooby vs Vert.x)"

application {
    mainClass = "benchmarks.Harness"
}

val benchmarkApps = mapOf(
    "javapi" to project(":benchmarks:apps:javapi-app"),
    "javalin" to project(":benchmarks:apps:javalin-app"),
    "jooby" to project(":benchmarks:apps:jooby-app"),
    "vertx" to project(":benchmarks:apps:vertx-app"),
)

fun appClasspath(name: String) =
    benchmarkApps.getValue(name).sourceSets.main.get().runtimeClasspath.asPath

fun appClasses() = benchmarkApps.values.map { it.tasks.named("classes").get() }

tasks.named<Test>("test") {
    dependsOn(appClasses())
    for (name in benchmarkApps.keys) {
        systemProperty("bench.app.$name.cp", appClasspath(name))
    }
}

tasks.register<JavaExec>("compare") {
    group = "benchmark"
    description = "Run the comparative benchmark: javapi vs Javalin vs Jooby vs Vert.x"
    dependsOn(appClasses())
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("benchmarks.Harness")
    for (name in benchmarkApps.keys) {
        systemProperty("bench.app.$name.cp", appClasspath(name))
    }
    // Forward args from the command line, e.g.:
    // ./gradlew :benchmarks:harness:compare --args="--requests 20000 --concurrency 32 --workload plaintext,json,routes"
}
