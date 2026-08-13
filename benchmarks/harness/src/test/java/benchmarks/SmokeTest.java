package benchmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Boots every framework's reference app in a subprocess and verifies the shared
 * workload (plaintext/json/routes) responds without failures. Uses a tiny load
 * profile so the build stays fast; the full measurement is {@code compare}.
 */
class SmokeTest {

    @Test
    void allAppsBootAndServeTheWorkload() throws Exception {
        List<Harness.App> apps = List.of(app("javapi"), app("javalin"), app("jooby"), app("vertx"));
        Harness.Options options = new Harness.Options(100, 8, 200, 9400,
                List.of("plaintext", "json", "routes"), List.of("javapi", "javalin", "jooby", "vertx"),
                false, 30, List.of(), true);

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

    private static Harness.App app(String name) {
        String cp = System.getProperty("bench.app." + name + ".cp");
        assertNotNull(cp, "-Dbench.app." + name + ".cp not set; run through the Gradle test task");
        return new Harness.App(name, "demo.BenchApp", cp);
    }
}
