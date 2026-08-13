plugins {
    id("javapi.java-conventions")
}

description = "javapi core: annotations, routing, request model, JSON codec seam. No third-party dependencies."

dependencies {
    testImplementation("com.h2database:h2:2.3.232")
}
