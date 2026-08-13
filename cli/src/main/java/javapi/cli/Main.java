package javapi.cli;

import java.util.List;

/**
 * javapi command-line interface.
 *
 * <pre>
 * javapi run &lt;mainClass&gt; [--cp ...] [--jvm ...]
 * javapi dev &lt;mainClass&gt; [--cp ...] [--src ...] [--jvm ...]
 * javapi bench [--url URL] [--requests N] [--concurrency C]
 * javapi bench --app &lt;mainClass&gt; [--cp ...] [--port P]
 * javapi jar &lt;mainClass&gt; [--cp ...] [--jvm ...]
 * javapi native &lt;mainClass&gt; [--cp ...] [--port P] [--urls "/p1,/p2"] [--o NAME]
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** Dispatch a command line and return the exit code (no System.exit). */
    public static int run(String[] args) {
        if (args.length == 0) {
            usage();
            return 2;
        }
        String command = args[0];
        List<String> rest = List.of(java.util.Arrays.copyOfRange(args, 1, args.length));
        try {
            return switch (command) {
                case "run" -> Commands.run(rest);
                case "dev" -> Commands.dev(rest);
                case "bench" -> Commands.bench(rest);
                case "jar" -> Commands.jar(rest);
                case "native" -> Commands.nativeImage(rest);
                case "help", "--help", "-h" -> {
                    usage();
                    yield 0;
                }
                case "version", "--version", "-v" -> {
                    System.out.println("javapi " + Version.get());
                    yield 0;
                }
                default -> {
                    System.err.println("Unknown command: " + command);
                    usage();
                    yield 2;
                }
            };
        } catch (CliException e) {
            System.err.println("javapi: " + e.getMessage());
            return 1;
        }
    }

    private static void usage() {
        System.out.println("""
                javapi - command-line tools for the javapi framework

                Usage:
                  javapi run <mainClass> [--cp <classpath>] [--jvm <arg>]...
                      Run an application in this process' JVM.

                  javapi dev <mainClass> [--cp <classpath>] [--src <dir>] [--jvm <arg>]...
                      Run an application and hot-reload it whenever a .java source changes.
                      Sources are compiled with javac and the server restarted on each change.

                  javapi bench [--url <url>] [--requests N] [--concurrency C]
                      Load-test a running server and print throughput + latency percentiles.
                      Use --app <mainClass> [--port P] to launch the server first and also
                      measure cold-start (time-to-first-request).

                  javapi jar <mainClass> [--cp <classpath>] [--jvm <arg>]...
                      Build an AppCDS archive and a jlink-trimmed runtime under build/javapi-image.

                  javapi native <mainClass> [--cp <classpath>] [--port P] [--urls "/p1,/p2"] [--o <name>]
                      Build a GraalVM native image (requires a GraalVM JDK with native-image).
                      Reflection config is captured by running the app under the tracing agent and
                      exercising the given routes (default "/"). Point --port at the app's port and
                      list every route that touches a DB/pool/record type so their metadata is captured.

                  javapi version
                  javapi help
                """);
    }
}
