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
}
