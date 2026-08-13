plugins {
    id("javapi.java-conventions")
}

description = "javapi server: Netty integration and lifecycle"

dependencies {
    implementation(project(":core"))
    implementation("io.netty:netty-codec-http:${property("netty.version")}")
}
