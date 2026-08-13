plugins {
    id("javapi.java-conventions")
    application
}

description = "Jooby benchmark app (plaintext/json/route-table)"

application {
    mainClass = "demo.BenchApp"
}

dependencies {
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("io.jooby:jooby-netty:3.11.7")
    implementation("io.jooby:jooby-jackson:3.11.7")
}

