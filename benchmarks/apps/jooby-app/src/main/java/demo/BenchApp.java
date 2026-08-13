package demo;

import java.util.Map;
import static io.jooby.Jooby.runApp;

/**
 * Jooby reference app for the comparative benchmark (same workload as the
 * other framework apps). Jooby 3 runs on Netty via {@code jooby-netty}.
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

        // Jooby reads server.port from system properties (Typesafe config merge).
        System.setProperty("server.port", Integer.toString(port));

        final int routeCount = routes;
        runApp(new String[0], app -> {
            app.get("/plaintext", ctx -> {
                ctx.setResponseType("text/plain");
                return "Hello, World!";
            });
            app.get("/json", ctx -> Map.of("message", "Hello, World!"));
            for (int i = 1; i <= routeCount; i++) {
                final int n = i;
                app.get("/r" + n, ctx -> Map.of("id", n));
            }
        });
    }
}
