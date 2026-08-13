plugins {
    id("javapi.java-conventions")
}

description = "javapi JSON adapter: Jackson codec for the core Json seam"

dependencies {
    implementation(project(":core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:${property("jackson.version")}")
}
