package benchmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LoadGeneratorTest {

    @Test
    void loadIssuesExactlyTheRequestedNumberOfRequestsAfterWarmup() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ping", exchange -> {
            hits.incrementAndGet();
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/ping";
            Harness.Result result = Harness.load(url, 500, 8, 100);

            assertEquals(600, hits.get(), "warmup + measured requests must both reach the server");
            assertEquals(0, result.failures());
            assertTrue(result.rps() > 0, "throughput must be positive");
            assertTrue(result.p50() <= result.p95(), "p50 must not exceed p95");
            assertTrue(result.p95() <= result.p99(), "p95 must not exceed p99");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void parseOptionsAcceptsWarmup() {
        Harness.Options options = Harness.parseOptions(List.of("--warmup", "5000"));
        assertEquals(5000, options.warmup());
    }

    @Test
    void paramsWorkloadResolvesToAParameterisedPath() {
        Harness.Options options = Harness.parseOptions(List.of("--workload", "params"));
        assertEquals(List.of("params"), options.workloads());
        assertEquals("http://localhost:9100/p1000/42", Harness.url("params", 9100, 1000));
    }

    @Test
    void parseOptionsAcceptsRepeatsAndMaxSpread() {
        Harness.Options options = Harness.parseOptions(
                List.of("--repeats", "3", "--max-spread", "5"));
        assertEquals(3, options.repeats());
        assertEquals(5.0, options.maxSpread(), 0.001);
    }

    @Test
    void spreadsComputeMinMaxAndSpreadPct() {
        Harness.Result r1 = new Harness.Result(0, 1000, 0, 0, 0, 0, -1);
        Harness.Result r2 = new Harness.Result(0, 1200, 0, 0, 0, 0, -1);
        List<List<Harness.Sample>> runs = List.of(
                List.of(new Harness.Sample("javapi", "plaintext", r1, 0)),
                List.of(new Harness.Sample("javapi", "plaintext", r2, 0)),
                List.of(new Harness.Sample("javapi", "plaintext", r1, 0)));

        Harness.Spread spread = Harness.spreads(runs).get(0);

        assertEquals(1000.0, spread.min(), 0.001);
        assertEquals(1200.0, spread.max(), 0.001);
        assertEquals(20.0, spread.spreadPct(), 0.001);
    }

    @Test
    void spreadGateFailsWhenJavapiSpreadExceedsLimit() {
        Harness.Result slow = new Harness.Result(0, 1000, 0, 0, 0, 0, -1);
        Harness.Result fast = new Harness.Result(0, 1200, 0, 0, 0, 0, -1);
        List<Harness.Spread> spread = List.of(
                new Harness.Spread("javapi", "plaintext", 1000, 1200, 20.0),
                new Harness.Spread("vertx", "json", 1000, 2000, 100.0));

        assertTrue(!Harness.spreadGate(spread, 5.0),
                "javapi spread above the limit must fail the gate");
        assertTrue(Harness.spreadGate(spread, 25.0),
                "javapi spread within the limit must pass even with a noisy competitor");
    }
}
