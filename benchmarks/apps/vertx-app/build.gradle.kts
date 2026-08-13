plugins {
    id("javapi.java-conventions")
    application
}

description = "Vert.x benchmark app (plaintext/json/route-table)"

application {
    mainClass = "demo.BenchApp"
}

dependencies {
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("io.vertx:vertx-web:4.5.27")
}

