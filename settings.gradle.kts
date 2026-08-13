rootProject.name = "javapi"

include("core")
include("server")
include("json-jackson")
include("config-yaml")
include("openapi")
include("cli")
include("testkit")
include("jdbc-pool")
include("examples")

include("benchmarks:harness")
include("benchmarks:apps:javapi-app")
include("benchmarks:apps:javalin-app")
include("benchmarks:apps:jooby-app")
include("benchmarks:apps:vertx-app")
