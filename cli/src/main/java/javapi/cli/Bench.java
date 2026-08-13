package javapi.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

final class Bench {

    private Bench() {
    }

    record Result(long startupMillis, double rps, double p50, double p95, double p99, long failures) {
    }

    /** Wait until {@code url} answers with any HTTP status, measuring time-to-first-request. */
    static long waitReady(String url) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        long start = System.nanoTime();
        long deadline = start + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                client.send(request, HttpResponse.BodyHandlers.discarding());
                return (System.nanoTime() - start) / 1_000_000;
            } catch (Exception ignored) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            }
        }
        throw new CliException("Timed out waiting for " + url + " to become ready");
    }

    static Result run(String url, int requests, int concurrency) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
        for (int i = 0; i < 200; i++) {
            try {
                client.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                throw new CliException("Benchmark request failed: " + e.getMessage());
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(concurrency,
                Thread.ofVirtual().name("javapi-bench-", 0).factory());
        List<Long> latencies = new ArrayList<>();
        AtomicLong failures = new AtomicLong();
        long start = System.nanoTime();
        List<CompletableFuture<Void>> futures = new ArrayList<>(requests);
        for (int i = 0; i < requests; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                long t0 = System.nanoTime();
                try {
                    client.send(request, HttpResponse.BodyHandlers.discarding());
                    synchronized (latencies) {
                        latencies.add(System.nanoTime() - t0);
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            }, pool));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        double elapsedSec = (System.nanoTime() - start) / 1e9;
        pool.shutdown();
        List<Long> sorted = new ArrayList<>(latencies);
        sorted.sort(Comparator.naturalOrder());
        long p50 = sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2);
        long p95 = sorted.isEmpty() ? 0 : sorted.get((int) Math.min(sorted.size() - 1, sorted.size() * 0.95));
        long p99 = sorted.isEmpty() ? 0 : sorted.get((int) Math.min(sorted.size() - 1, sorted.size() * 0.99));
        return new Result(0, requests / elapsedSec,
                p50 / 1e6, p95 / 1e6, p99 / 1e6, failures.get());
    }
}
