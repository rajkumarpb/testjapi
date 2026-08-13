plugins {
    id("javapi.java-conventions")
}

description = "javapi optional HikariCP-backed DataSource (ServiceLoader adapter)"

dependencies {
    implementation(project(":core"))
    implementation("com.zaxxer:HikariCP:5.1.0")
    testImplementation("com.h2database:h2:2.3.232")
}
