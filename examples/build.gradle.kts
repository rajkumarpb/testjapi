plugins {
    id("javapi.java-conventions")
    application
}

description = "javapi example apps"

application {
    mainClass = "demo.App"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":server"))
    implementation(project(":openapi"))
    implementation(project(":jdbc-pool"))
    implementation("com.h2database:h2:2.3.232")
    testImplementation(project(":testkit"))
}
