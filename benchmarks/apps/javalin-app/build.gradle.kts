plugins {
    id("javapi.java-conventions")
    application
}

description = "Javalin benchmark app (plaintext/json/route-table)"

application {
    mainClass = "demo.BenchApp"
}

dependencies {
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("io.javalin:javalin:6.7.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.3")
}

