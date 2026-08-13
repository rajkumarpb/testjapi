plugins {
    id("javapi.java-conventions")
    application
}

description = "javapi CLI: run, dev (hot reload), bench, native, jar"

application {
    mainClass = "javapi.cli.Main"
    applicationName = "javapi"
}

tasks.named<CreateStartScripts>("startScripts") {
    applicationName = "javapi"
}

tasks.processResources {
    expand(mapOf("version" to project.version))
}

dependencies {
}
