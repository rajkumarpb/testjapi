package demo;

import java.util.Map;
import javapi.core.JavAPI;
import javapi.request.Response;

/**
 * javapi reference app for the comparative benchmark. Serves the same
 * TechEmpower-style workload as the Javalin/Jooby/Vert.x counterparts:
 * {@code /plaintext}, {@code /json} and a generated route table.
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

        JavAPI app = JavAPI.create()
                .port(port)
                .eventLoopInline(true)
                .get("/plaintext", request -> Response.of(200, "Hello, World!")
                        .withHeader("Content-Type", "text/plain"))
                .get("/json", request -> Map.of("message", "Hello, World!"));
        for (int i = 1; i <= routes; i++) {
            final int n = i;
            app.get("/r" + n, request -> Map.of("id", n));
        }

        try {
            app.start().await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
