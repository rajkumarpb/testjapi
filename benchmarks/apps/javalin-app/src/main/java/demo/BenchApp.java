package demo;

import java.util.Map;
import io.javalin.Javalin;

/**
 * Javalin reference app for the comparative benchmark (same workload as the
 * other framework apps). Javalin 6 uses Jetty 12.
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

        Javalin app = Javalin.create()
                .get("/plaintext", ctx -> {
                    ctx.contentType("text/plain");
                    ctx.result("Hello, World!");
                })
                .get("/json", ctx -> ctx.json(Map.of("message", "Hello, World!")));
        for (int i = 1; i <= routes; i++) {
            final int n = i;
            app.get("/r" + n, ctx -> ctx.json(Map.of("id", n)));
            app.get("/p" + n + "/{id}", ctx -> ctx.json(Map.of("route", n, "id", ctx.pathParam("id"))));
        }

        app.start(port);
    }
}
