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
    doLast {
        val out = layout.buildDirectory.file("resources/main/javapi/cli/version.txt").get().asFile
        out.parentFile.mkdirs()
        out.writeText("${project.version}\n")
    }
}

dependencies {
}
