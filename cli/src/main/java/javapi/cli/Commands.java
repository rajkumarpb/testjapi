package javapi.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

final class Commands {

    private Commands() {
    }

    static int run(List<String> args) {
        ArgOptions options = new ArgOptions(args);
        String main = options.positional();
        if (main == null) {
            throw new CliException("run requires a main class");
        }
        String classpath = Classpaths.resolve(options.opt("--cp"));
        List<String> jvmArgs = options.collect("--jvm");
        Process process = Processes.startJava(main, classpath, jvmArgs, List.of());
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }

    static int dev(List<String> args) {
        ArgOptions options = new ArgOptions(args);
        String main = options.positional();
        if (main == null) {
            throw new CliException("dev requires a main class");
        }
        String runtimeClasspath = Classpaths.resolve(options.opt("--cp"));
        Path src = Path.of(options.opt("--src") == null ? "src" : options.opt("--src"));
        Path out = Path.of("build", "classes", "dev");
        List<String> jvmArgs = options.collect("--jvm");

        compile(src, out, runtimeClasspath);
        String classpath = Classpaths.withOutput(out, runtimeClasspath);
        AtomicReference<Process> current =
                new AtomicReference<>(Processes.startJava(main, classpath, jvmArgs, List.of()));

        Watcher watcher;
        try {
            watcher = new Watcher(src, changed -> {
                System.out.println("[javapi] change detected: " + changed.get(0));
                Processes.destroy(current.get());
                try {
                    compile(src, out, runtimeClasspath);
                    current.set(Processes.startJava(
                            main, Classpaths.withOutput(out, runtimeClasspath), jvmArgs, List.of()));
                    System.out.println("[javapi] restarted");
                } catch (CliException e) {
                    System.err.println("[javapi] rebuild failed: " + e.getMessage() + " - server stopped");
                }
            });
        } catch (IOException e) {
            throw new CliException("Failed to watch " + src, e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Processes.destroy(current.get());
            watcher.close();
        }));
        System.out.println("[javapi] watching " + src + " for changes (Ctrl+C to stop)");
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 0;
    }

    static int bench(List<String> args) {
        ArgOptions options = new ArgOptions(args);
        String url = options.opt("--url");
        String app = options.opt("--app");
        int requests = options.optInt("--requests", 10_000);
        int concurrency = options.optInt("--concurrency", 64);
        if (app == null) {
            if (url == null) {
                throw new CliException("bench requires --url <url> or --app <mainClass>");
            }
            print(options, url, requests, concurrency, Bench.run(url, requests, concurrency));
            return 0;
        }
        String classpath = Classpaths.resolve(options.opt("--cp"));
        int port = options.optInt("--port", 8080);
        Process process = Processes.startJava(app, classpath, List.of(), List.of());
        String target = url != null ? url : "http://localhost:" + port + "/";
        try {
            long startup = Bench.waitReady(target);
            System.out.println("cold start (time-to-first-request): " + startup + " ms");
            print(options, target, requests, concurrency, Bench.run(target, requests, concurrency));
        } finally {
            Processes.destroy(process);
        }
        return 0;
    }

    private static void print(ArgOptions options, String url, int requests, int concurrency, Bench.Result result) {
        System.out.println("target:      " + url);
        System.out.println("requests:    " + requests);
        System.out.println("concurrency: " + concurrency);
        System.out.println("failures:    " + result.failures());
        System.out.printf("throughput:  %.0f req/s%n", result.rps());
        System.out.printf("latency p50: %.2f ms%n", result.p50());
        System.out.printf("latency p95: %.2f ms%n", result.p95());
        System.out.printf("latency p99: %.2f ms%n", result.p99());
    }

    static int jar(List<String> args) {
        ArgOptions options = new ArgOptions(args);
        String main = options.positional();
        if (main == null) {
            throw new CliException("jar requires a main class");
        }
        String classpath = Classpaths.resolve(options.opt("--cp"));
        List<String> jvmArgs = options.collect("--jvm");
        Path image = Path.of("build", "javapi-image");
        Path jsa = image.resolve("app.jsa");
        try {
            Files.createDirectories(image);
        } catch (IOException e) {
            throw new CliException("Failed to create " + image, e);
        }

        System.out.println("[javapi] generating AppCDS archive...");
        Process archive = Processes.startJava(main, classpath, List.of(
                "-XX:ArchiveClassesAtExit=" + jsa.toAbsolutePath(),
                "-Xshare:auto"), jvmArgs);
        try {
            long startup = Bench.waitReady("http://localhost:8080/");
            System.out.println("[javapi] app ready in " + startup + " ms, shutting down to dump archive...");
            Processes.destroy(archive);
        } catch (CliException e) {
            System.out.println("[javapi] app did not listen on 8080 - CDS archive will not be generated (" + e.getMessage() + ")");
        }
        if (archive.isAlive()) {
            archive.destroyForcibly();
        }

        Path runtime = image.resolve("runtime");
        System.out.println("[javapi] building jlink runtime into " + runtime + "...");
        runProcess("jlink",
                "--add-modules", "java.base,java.logging,java.management,java.net.http,java.naming,java.sql,jdk.unsupported,jdk.zipfs",
                "--output", runtime.toAbsolutePath().toString(),
                "--strip-debug", "--no-man-pages", "--no-header-files");

        System.out.println("[javapi] writing launcher scripts...");
        try {
            writeLaunchers(image, runtime, jsa, classpath, main);
        } catch (IOException e) {
            throw new CliException("Failed to write launcher scripts: " + e.getMessage(), e);
        }

        System.out.println("[javapi] done. Launch with:");
        if (Processes.isWindows()) {
            System.out.println("    build\\javapi-image\\run.bat");
        } else {
            System.out.println("    build/javapi-image/run");
        }
        return 0;
    }

    private static void writeLaunchers(Path image, Path runtime, Path jsa,
            String classpath, String main) throws IOException {
        String archiveFlag = Files.isRegularFile(jsa)
                ? " -XX:SharedArchiveFile=\"" + jsa.toAbsolutePath() + "\""
                : "";
        if (Processes.isWindows()) {
            String bat = "@echo off\r\n"
                    + "setlocal\r\n"
                    + "\"" + runtime.resolve("bin").resolve("java.exe").toAbsolutePath() + "\""
                    + archiveFlag
                    + " -cp \"" + classpath + "\" " + main + " %*\r\n";
            Files.writeString(image.resolve("run.bat"), bat);
        } else {
            String script = "#!/usr/bin/env bash\n"
                    + "DIR=\"$(cd \"$(dirname \"${BASH_SOURCE[0]}\")\" && pwd)\"\n"
                    + "exec \"$DIR/runtime/bin/java\"" + archiveFlag
                    + " -cp \"" + classpath + "\" " + main + " \"$@\"\n";
            Path launcher = image.resolve("run");
            Files.writeString(launcher, script);
            launcher.toFile().setExecutable(true);
        }
    }

    static int nativeImage(List<String> args) {
        ArgOptions options = new ArgOptions(args);
        String main = options.positional();
        if (main == null) {
            throw new CliException("native requires a main class");
        }
        String classpath = Classpaths.resolve(options.opt("--cp"));
        String name = options.opt("--o") == null ? "app" : options.opt("--o");
        int port = options.optInt("--port", 8080);
        List<String> targets = captureTargets(options.opt("--urls"), port);
        String nativeImage = findNativeImage();

        Path configDir = Path.of("build", "native-config");
        clearDirectory(configDir);

        System.out.println("[javapi] capturing reflection config with the tracing agent...");
        Process agent = Processes.startJava(main, classpath, List.of(
                "-agentlib:native-image-agent=config-output-dir=" + configDir.toAbsolutePath()), List.of());
        try {
            if (!survivesLaunch(agent)) {
                System.err.println("[javapi] the app exited before capture started - the native-image tracing "
                        + "agent only runs on a GraalVM JDK. Install GraalVM and set GRAALVM_HOME, or put "
                        + "GraalVM's bin on PATH.");
                return 1;
            }
            long startup = Bench.waitReady(targets.get(0));
            System.out.println("[javapi] app ready in " + startup + " ms, exercising " + targets.size() + " route(s)...");
            for (String target : targets) {
                try {
                    Bench.run(target, 20, 4);
                    System.out.println("[javapi]   " + target + " ok");
                } catch (CliException e) {
                    System.out.println("[javapi]   " + target + " not exercised: " + e.getMessage());
                }
            }
        } catch (CliException e) {
            System.out.println("[javapi] could not reach app on port " + port + ": " + e.getMessage());
        } finally {
            Processes.destroy(agent);
        }

        if (!hasJsonConfig(configDir)) {
            System.err.println("[javapi] no reflection config was captured under " + configDir
                    + " - the native image would be missing runtime metadata. Aborting.");
            return 1;
        }

        System.out.println("[javapi] invoking native-image...");
        runProcess(nativeImage,
                "-cp", classpath,
                "-H:ConfigurationFileDirectories=" + configDir.toAbsolutePath(),
                "--no-fallback",
                "-o", name,
                main);
        System.out.println("[javapi] done: " + Path.of(name).toAbsolutePath());
        return 0;
    }

    /** Build the list of {@code http://localhost:port<path>} URLs to exercise during config capture. */
    static List<String> captureTargets(String urls, int port) {
        List<String> targets = new ArrayList<>();
        if (urls == null || urls.isBlank()) {
            targets.add(url(port, "/"));
            return targets;
        }
        for (String part : urls.split(",")) {
            String path = part.trim();
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            targets.add(url(port, path));
        }
        return targets;
    }

    private static String url(int port, String path) {
        return "http://localhost:" + port + path;
    }

    /** True if the process is still alive a moment after launch (detects agent-load failures fast). */
    private static boolean survivesLaunch(Process process) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                return false;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return process.isAlive();
            }
        }
        return process.isAlive();
    }

    static boolean hasJsonConfig(Path configDir) {
        if (!Files.isDirectory(configDir)) {
            return false;
        }
        try (var stream = Files.walk(configDir)) {
            return stream.anyMatch(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().endsWith(".json"));
        } catch (IOException e) {
            return false;
        }
    }

    private static void clearDirectory(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                }
            }
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new CliException("Failed to prepare " + dir, e);
        }
    }

    private static String findNativeImage() {
        String graalvm = System.getenv("GRAALVM_HOME");
        if (graalvm != null && !graalvm.isBlank()) {
            Path candidate = Path.of(graalvm, "bin", "native-image");
            if (Files.isRegularFile(candidate) || Files.isRegularFile(Path.of(graalvm, "bin", "native-image.cmd"))) {
                return candidate.toAbsolutePath().toString();
            }
        }
        return "native-image";
    }

    private static void compile(Path src, Path out, String runtimeClasspath) {
        try {
            Files.createDirectories(out);
            List<Path> sources = new ArrayList<>();
            try (var stream = Files.walk(src)) {
                stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                        .sorted()
                        .forEach(sources::add);
            }
            if (sources.isEmpty()) {
                throw new CliException("No .java sources found under " + src);
            }
            Path argfile = Files.createTempFile("javapi-javac", ".args");
            StringBuilder sb = new StringBuilder();
            sb.append("-parameters\n");
            sb.append("-encoding\nUTF-8\n");
            sb.append("-d\n").append(out.toAbsolutePath()).append('\n');
            if (!runtimeClasspath.isEmpty()) {
                sb.append("-cp\n").append(runtimeClasspath).append('\n');
            }
            for (Path source : sources) {
                sb.append(source.toAbsolutePath()).append('\n');
            }
            Files.writeString(argfile, sb.toString());
            Process process = new ProcessBuilder(Processes.javacBin(), "@" + argfile)
                    .redirectErrorStream(true)
                    .start();
            try (var in = process.getInputStream()) {
                in.transferTo(System.out);
            }
            int code = process.waitFor();
            if (code != 0) {
                throw new CliException("javac failed with exit code " + code);
            }
        } catch (IOException e) {
            throw new CliException("Compilation failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CliException("Compilation interrupted", e);
        }
    }

    private static void runProcess(String executable, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new CliException("Failed to run " + executable + ": " + e.getMessage()
                    + (executable.equals("jlink") ? " (is this a JDK build?)" : ""), e);
        }
        try (var in = process.getInputStream()) {
            in.transferTo(System.out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            int code = process.waitFor();
            if (code != 0) {
                throw new CliException(executable + " exited with code " + code);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CliException(executable + " interrupted", e);
        }
    }
}
