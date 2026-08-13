package demo;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

/**
 * Vert.x reference app for the comparative benchmark (same workload as the
 * other framework apps). Vert.x 4 event-loop model.
 */
public final class BenchApp {

    private BenchApp() {
    }

    public static void main(String[] args) {
        int port = 8000;
        int routes = 1000;
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--port".equals(args[i])) {
                port = Integer.parseInt(args[++i]);
            } else if ("--routes".equals(args[i])) {
                routes = Integer.parseInt(args[++i]);
            }
        }

        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);

        router.get("/plaintext").handler(ctx -> ctx.response()
                .putHeader("Content-Type", "text/plain")
                .end("Hello, World!"));
        router.get("/json").handler(ctx -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("message", "Hello, World!").encode()));
        for (int i = 1; i <= routes; i++) {
            final int n = i;
            router.get("/r" + n).handler(ctx -> ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("id", n).encode()));
        }

        vertx.createHttpServer().requestHandler(router).listen(port);
    }
}
