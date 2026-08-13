package benchmarks;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comparative benchmark harness. Spawns each framework's reference app on its
 * own port, measures time-to-first-request, throughput and latency percentiles
 * on the same TechEmpower-style workload, then prints a table and enforces the
 * §10.6 gates:
 *
 * <ul>
 *   <li>javapi must beat both Javalin and Jooby on every workload;</li>
 *   <li>javapi must stay within 10% of Vert.x on CPU-bound tests.</li>
 * </ul>
 *
 * <p>Classpaths for the apps are injected as {@code -Dbench.app.<name>.cp}
 * system properties (set by the Gradle {@code compare}/{@code test} tasks), so
 * the harness itself has no dependency on the framework jars.
 */
public final class Harness {

    private Harness() {
    }

    record App(String name, String mainClass, String classpath) {
    }

    record Options(int requests, int concurrency, int routes, int basePort,
            List<String> workloads, List<String> apps, boolean gates, int readyTimeoutSeconds) {
    }

    record Result(long startupMillis, double rps, double p50, double p95, double p99, long failures,
            long maxRssKb) {
    }

    record Sample(String app, String workload, Result result, double libMb) {
    }

    public static void main(String[] args) throws Exception {
        Options options = parseOptions(List.of(args));
        List<App> apps = resolveApps(options.apps());
        List<Sample> samples = runAll(apps, options);
        printTable(samples);
        if (options.gates()) {
            System.exit(gates(samples) ? 0 : 1);
        }
    }

    static Options parseOptions(List<String> args) {
        int requests = 10_000;
        int concurrency = 32;
        int routes = 1_000;
        int basePort = 9100;
        boolean gates = true;
        int readyTimeout = 60;
        List<String> workloads = List.of("plaintext", "json", "routes");
        List<String> apps = List.of("javapi", "javalin", "jooby", "vertx");
        for (int i = 0; i < args.size(); i++) {
            switch (args.get(i)) {
                case "--requests" -> requests = Integer.parseInt(args.get(++i));
                case "--concurrency" -> concurrency = Integer.parseInt(args.get(++i));
                case "--routes" -> routes = Integer.parseInt(args.get(++i));
                case "--base-port" -> basePort = Integer.parseInt(args.get(++i));
                case "--ready-timeout" -> readyTimeout = Integer.parseInt(args.get(++i));
                case "--workload" -> workloads = List.of(args.get(++i).split(","));
                case "--apps" -> apps = List.of(args.get(++i).split(","));
                case "--no-gates" -> gates = false;
                default -> throw new IllegalArgumentException("Unknown option: " + args.get(i));
            }
        }
        return new Options(requests, concurrency, routes, basePort, workloads, apps, gates, readyTimeout);
    }

    private static List<App> resolveApps(List<String> names) {
        List<App> apps = new ArrayList<>();
        for (String name : names) {
            String cp = System.getProperty("bench.app." + name + ".cp");
            if (cp == null || cp.isBlank()) {
                throw new IllegalStateException("Missing -Dbench.app." + name
                        + ".cp; run through the Gradle compare/test task which injects it");
            }
            apps.add(new App(name, "demo.BenchApp", cp));
        }
        return apps;
    }

    static List<Sample> runAll(List<App> apps, Options options) throws IOException {
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            App app = apps.get(i);
            int port = options.basePort() + i;
            Process process = spawn(app, port, options.routes());
            try {
            long startup = waitReady(url("plaintext", port, options.routes()),
                    options.readyTimeoutSeconds());
            List<Result> results = new ArrayList<>();
            for (String workload : options.workloads()) {
                results.add(load(url(workload, port, options.routes()),
                        options.requests(), options.concurrency()));
            }
            long maxRssKb = isWindows() ? -1 : readRssKb(process.pid());
            List<String> workloads = options.workloads();
            for (int j = 0; j < results.size(); j++) {
                Result result = results.get(j);
                samples.add(new Sample(app.name(), workloads.get(j),
                        new Result(startup, result.rps(), result.p50(), result.p95(), result.p99(),
                                result.failures(), maxRssKb),
                        libSizeMb(app.classpath())));
            }
            } finally {
                destroy(process);
            }
        }
        return samples;
    }

    private static String url(String workload, int port, int routes) {
        return switch (workload) {
            case "plaintext" -> "http://localhost:" + port + "/plaintext";
            case "json" -> "http://localhost:" + port + "/json";
            case "routes" -> "http://localhost:" + port + "/r" + routes;
            default -> throw new IllegalArgumentException("Unknown workload: " + workload);
        };
    }

    private static Process spawn(App app, int port, int routes) throws IOException {
        String java = Path.of(System.getProperty("java.home"),
                "bin", "java" + (isWindows() ? ".exe" : "")).toString();
        List<String> command = new ArrayList<>();
        command.add(java);
        command.add("-cp");
        command.add(app.classpath());
        command.add(app.mainClass());
        command.add("--port");
        command.add(String.valueOf(port));
        command.add("--routes");
        command.add(String.valueOf(routes));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        Thread.ofVirtual().name("drain-" + app.name(), 0).start(() -> {
            try (var reader = process.getInputStream()) {
                reader.transferTo(System.out);
            } catch (IOException ignored) {
            }
        });
        return process;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void destroy(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    /** Wait until {@code url} answers with any HTTP status, measuring time-to-first-request. */
    static long waitReady(String url, int timeoutSeconds) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        long start = System.nanoTime();
        long deadline = start + Duration.ofSeconds(timeoutSeconds).toNanos();
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
        throw new IllegalStateException("Timed out waiting for " + url + " to become ready");
    }

    static Result load(String url, int requests, int concurrency) {
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
                throw new IllegalStateException("Benchmark request failed: " + e.getMessage(), e);
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(concurrency,
                Thread.ofVirtual().name("bench-", 0).factory());
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
                p50 / 1e6, p95 / 1e6, p99 / 1e6, failures.get(), -1);
    }

    /**
     * Peak resident set size of a live process from {@code /proc/<pid>/status}
     * (VmHWM). Returns -1 when unavailable; only meaningful on Linux.
     */
    private static long readRssKb(long pid) {
        try {
            Path status = Path.of("/proc/" + pid + "/status");
            for (String line : Files.readAllLines(status)) {
                if (line.startsWith("VmHWM:")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        return Long.parseLong(parts[1]);
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return -1;
    }

    private static double libSizeMb(String classpath) {
        double bytes = 0;
        for (String entry : classpath.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            Path p = Path.of(entry);
            if (p.toString().endsWith(".jar") && java.nio.file.Files.isRegularFile(p)) {
                try {
                    bytes += java.nio.file.Files.size(p);
                } catch (IOException ignored) {
                }
            }
        }
        return bytes / (1024.0 * 1024.0);
    }

    private static void printTable(List<Sample> samples) {
        String header = String.format("%-8s %-10s %-10s %-10s %-10s %-10s %-10s %-10s %-10s",
                "app", "workload", "startup(ms)", "req/s", "p50(ms)", "p95(ms)", "p99(ms)", "lib(MB)", "rss(MB)");
        System.out.println(header);
        System.out.println("-".repeat(header.length()));
        for (Sample s : samples) {
            System.out.printf("%-8s %-10s %-10d %-10.0f %-10.1f %-10.1f %-10.1f %-10.1f %-10s%n",
                    s.app(), s.workload(), s.result().startupMillis(), s.result().rps(),
                    s.result().p50(), s.result().p95(), s.result().p99(), s.libMb(),
                    s.result().maxRssKb() < 0 ? "n/a"
                            : String.format("%.1f", s.result().maxRssKb() / 1024.0));
        }
    }

    /**
     * Enforce the §10.6 gates per workload: javapi beats Javalin and Jooby and
     * stays within 10% of Vert.x. Returns true when every gate passes.
     */
    static boolean gates(List<Sample> samples) {
        boolean allPass = true;
        for (String workload : workloads(samples)) {
            double javapi = rps(samples, "javapi", workload);
            double javalin = rps(samples, "javalin", workload);
            double jooby = rps(samples, "jooby", workload);
            double vertx = rps(samples, "vertx", workload);
            boolean beatJavalin = javapi >= javalin;
            boolean beatJooby = javapi >= jooby;
            boolean closeToVertx = vertx == 0 || javapi >= 0.9 * vertx;
            boolean pass = beatJavalin && beatJooby && closeToVertx;
            allPass &= pass;
            System.out.printf("%-10s javapi %6.0f | javalin %6.0f | jooby %6.0f | vertx %6.0f | %s%n",
                    workload, javapi, javalin, jooby, vertx, pass ? "PASS" : "FAIL");
        }
        return allPass;
    }

    private static List<String> workloads(List<Sample> samples) {
        return samples.stream().map(Sample::workload).distinct().toList();
    }

    private static double rps(List<Sample> samples, String app, String workload) {
        return samples.stream()
                .filter(s -> s.app().equals(app) && s.workload().equals(workload))
                .findFirst()
                .map(s -> s.result().rps())
                .orElse(0.0);
    }
}
