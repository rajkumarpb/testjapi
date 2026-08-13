package javapi.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class Processes {

    private Processes() {
    }

    static Process startJava(String mainClass, String classpath,
            List<String> jvmArgs, List<String> appArgs) {
        List<String> command = new java.util.ArrayList<>();
        command.add(javaBin());
        command.addAll(jvmArgs);
        if (!classpath.isEmpty()) {
            command.add("-cp");
            command.add(classpath);
        }
        command.add(mainClass);
        command.addAll(appArgs);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        try {
            return builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to launch " + mainClass, e);
        }
    }

    static String javaBin() {
        String home = System.getProperty("java.home");
        Path bin = Path.of(home, "bin", "java");
        return isWindows() ? bin + ".exe" : bin.toString();
    }

    static String javacBin() {
        String home = System.getProperty("java.home");
        Path bin = Path.of(home, "bin", "javac");
        return isWindows() ? bin + ".exe" : bin.toString();
    }

    static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("win");
    }

    static void destroy(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
