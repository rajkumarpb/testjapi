plugins {
    id("javapi.java-conventions")
}

description = "javapi OpenAPI 3.1 generation + Swagger UI/ReDoc"

dependencies {
    implementation(project(":core"))
    testImplementation(project(":server"))
}
