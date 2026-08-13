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
import java.util.Arrays;
import java.util.List;
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

    record Options(int requests, int concurrency, int warmup, int routes, int basePort,
            List<String> workloads, List<String> apps, boolean gates, int readyTimeoutSeconds,
            List<String> gatedWorkloads, boolean gateJooby, double vertxRatio) {
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
            System.exit(gates(samples, options.gatedWorkloads(), options.gateJooby(),
                    options.vertxRatio()) ? 0 : 1);
        }
    }

    static Options parseOptions(List<String> args) {
        int requests = 10_000;
        int concurrency = 32;
        int warmup = 10_000;
        int routes = 1_000;
        int basePort = 9100;
        boolean gates = true;
        int readyTimeout = 60;
        List<String> workloads = List.of("plaintext", "json", "routes");
        List<String> apps = List.of("javapi", "javalin", "jooby", "vertx");
        List<String> gatedWorkloads = List.of("plaintext", "json", "routes");
        boolean gateJooby = true;
        double vertxRatio = 0.9;
        for (int i = 0; i < args.size(); i++) {
            switch (args.get(i)) {
                case "--requests" -> requests = Integer.parseInt(args.get(++i));
                case "--concurrency" -> concurrency = Integer.parseInt(args.get(++i));
                case "--warmup" -> warmup = Integer.parseInt(args.get(++i));
                case "--routes" -> routes = Integer.parseInt(args.get(++i));
                case "--base-port" -> basePort = Integer.parseInt(args.get(++i));
                case "--ready-timeout" -> readyTimeout = Integer.parseInt(args.get(++i));
                case "--workload" -> workloads = List.of(args.get(++i).split(","));
                case "--apps" -> apps = List.of(args.get(++i).split(","));
                case "--gated" -> gatedWorkloads = List.of(args.get(++i).split(","));
                case "--no-gate-jooby" -> gateJooby = false;
                case "--vertx-ratio" -> vertxRatio = Double.parseDouble(args.get(++i));
                case "--no-gates" -> gates = false;
                default -> throw new IllegalArgumentException("Unknown option: " + args.get(i));
            }
        }
        return new Options(requests, concurrency, warmup, routes, basePort, workloads, apps, gates,
                readyTimeout, gatedWorkloads, gateJooby, vertxRatio);
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
                        options.requests(), options.concurrency(), options.warmup()));
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

    static String url(String workload, int port, int routes) {
        return switch (workload) {
            case "plaintext" -> "http://localhost:" + port + "/plaintext";
            case "json" -> "http://localhost:" + port + "/json";
            case "routes" -> "http://localhost:" + port + "/r" + routes;
            case "params" -> "http://localhost:" + port + "/p" + routes + "/42";
            default -> throw new IllegalArgumentException("Unknown workload: " + workload);
        };
    }

    static Process spawn(App app, int port, int routes) throws IOException {
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

    /**
     * Issue {@code warmup} un-measured requests to let C2 compile the server's
     * hot path, then {@code requests} measured requests across {@code
     * concurrency} fixed workers. Each worker owns a preallocated primitive
     * latency array, so the measurement path has no lock, no boxing and no
     * per-request allocation.
     */
    static Result load(String url, int requests, int concurrency, int warmup) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        drive(client, request, warmup, concurrency, null, new AtomicLong());

        int perWorker = (requests + concurrency - 1) / concurrency;
        long[][] samples = new long[concurrency][perWorker];
        AtomicLong failures = new AtomicLong();
        long start = System.nanoTime();
        int issued = drive(client, request, requests, concurrency, samples, failures);
        double elapsedSec = (System.nanoTime() - start) / 1e9;

        long[] sorted = new long[issued - (int) failures.get()];
        int at = 0;
        for (long[] worker : samples) {
            for (long sample : worker) {
                if (sample > 0 && at < sorted.length) {
                    sorted[at++] = sample;
                }
            }
        }
        sorted = Arrays.copyOf(sorted, at);
        Arrays.sort(sorted);
        return new Result(0, issued / elapsedSec,
                percentile(sorted, 0.50), percentile(sorted, 0.95), percentile(sorted, 0.99),
                failures.get(), -1);
    }

    /**
     * Run {@code total} requests across {@code concurrency} platform threads,
     * distributing the remainder so exactly {@code total} requests are issued.
     * When {@code samples} is non-null, worker {@code w} writes its latencies
     * into {@code samples[w]}. Returns the number of requests actually issued.
     */
    private static int drive(HttpClient client, HttpRequest request, int total, int concurrency,
            long[][] samples, AtomicLong failures) {
        if (total <= 0) {
            return 0;
        }
        int base = total / concurrency;
        int extra = total % concurrency;
        Thread[] workers = new Thread[concurrency];
        for (int w = 0; w < concurrency; w++) {
            final int worker = w;
            final int count = base + (w < extra ? 1 : 0);
            workers[w] = Thread.ofPlatform().name("bench-" + w).unstarted(() -> {
                for (int i = 0; i < count; i++) {
                    long t0 = System.nanoTime();
                    try {
                        client.send(request, HttpResponse.BodyHandlers.discarding());
                        if (samples != null) {
                            samples[worker][i] = System.nanoTime() - t0;
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                }
            });
            workers[w].start();
        }
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return total;
    }

    private static double percentile(long[] sortedNanos, double fraction) {
        if (sortedNanos.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(fraction * sortedNanos.length) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sortedNanos.length) {
            index = sortedNanos.length - 1;
        }
        return sortedNanos[index] / 1e6;
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
     * Enforce the §10.6 gates on the gated workloads: javapi must beat Javalin
     * (and Jooby unless {@code gateJooby} is disabled) and stay within
     * {@code vertxRatio} of Vert.x. Workloads not in {@code gated} are reported
     * as informational (never fail the build). Returns true when every gated
     * workload passes.
     */
    static boolean gates(List<Sample> samples, List<String> gated, boolean gateJooby,
            double vertxRatio) {
        boolean allPass = true;
        for (String workload : workloads(samples)) {
            boolean isGated = gated.contains(workload);
            double javapi = rps(samples, "javapi", workload);
            double javalin = rps(samples, "javalin", workload);
            double jooby = rps(samples, "jooby", workload);
            double vertx = rps(samples, "vertx", workload);
            boolean beatJavalin = javapi >= javalin;
            boolean beatJooby = !gateJooby || javapi >= jooby;
            boolean closeToVertx = vertx == 0 || javapi >= vertxRatio * vertx;
            boolean pass = beatJavalin && beatJooby && closeToVertx;
            if (isGated) {
                allPass &= pass;
            }
            String annotation = isGated ? "" : " (informational)";
            if (!gateJooby) {
                annotation += " (jooby not gated)";
            }
            System.out.printf("%-10s javapi %6.0f | javalin %6.0f | jooby %6.0f | vertx %6.0f | %s%s%n",
                    workload, javapi, javalin, jooby, vertx, isGated ? (pass ? "PASS" : "FAIL") : "INFO",
                    annotation);
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
