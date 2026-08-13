plugins {
    id("javapi.java-conventions")
}

description = "javapi testkit: in-process TestClient"

dependencies {
    implementation(project(":core"))
    implementation(project(":server"))
}
