plugins {
    id("javapi.java-conventions")
    application
}

description = "javapi benchmark app (plaintext/json/route-table)"

application {
    mainClass = "demo.BenchApp"
}

dependencies {
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation(project(":core"))
    implementation(project(":server"))
}

