package benchmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Boots every framework's reference app in a subprocess and verifies the shared
 * workload (plaintext/json/routes/params) responds without failures. Uses a
 * tiny load profile so the build stays fast; the full measurement is
 * {@code compare}.
 */
class SmokeTest {

    @Test
    void allAppsBootAndServeTheWorkload() throws Exception {
        List<Harness.App> apps = List.of(app("javapi"), app("javalin"), app("jooby"), app("vertx"));
        Harness.Options options = new Harness.Options(100, 8, 200, 200, 9400,
                List.of("plaintext", "json", "routes", "params"),
                List.of("javapi", "javalin", "jooby", "vertx"),
                false, 30, List.of(), true, 0.9);

        List<Harness.Sample> samples = Harness.runAll(apps, options);

        assertEquals(apps.size() * options.workloads().size(), samples.size(),
                "expected one sample per app per workload");
        for (Harness.Sample sample : samples) {
            assertTrue(sample.result().startupMillis() >= 0,
                    sample.app() + "/" + sample.workload() + " did not report startup time");
            assertTrue(sample.result().rps() > 0,
                    sample.app() + "/" + sample.workload() + " served no requests");
            assertEquals(0, sample.result().failures(),
                    sample.app() + "/" + sample.workload() + " reported failed requests");
        }
    }

    /**
     * A params route that 404s would still be counted as a successful load by
     * the harness (an HTTP exchange completed). This test boots each app and
     * asserts the actual status code, so a mis-registered {@code /p{n}/:id}
     * route cannot silently skew the params workload.
     */
    @Test
    void paramsRoutesReturn200ForEveryApp() throws Exception {
        List<Harness.App> apps = List.of(app("javapi"), app("javalin"), app("jooby"), app("vertx"));
        HttpClient client = HttpClient.newHttpClient();
        for (int i = 0; i < apps.size(); i++) {
            Harness.App app = apps.get(i);
            int port = 9600 + i;
            Process process = Harness.spawn(app, port, 200);
            try {
                String url = "http://localhost:" + port + "/p200/42";
                Harness.waitReady(url, 30);
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create(url)).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(),
                        app.name() + " must serve /p200/42, got HTTP " + response.statusCode());
            } finally {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        }
    }

    private static Harness.App app(String name) {
        String cp = System.getProperty("bench.app." + name + ".cp");
        assertNotNull(cp, "-Dbench.app." + name + ".cp not set; run through the Gradle test task");
        return new Harness.App(name, "demo.BenchApp", cp);
    }
}
