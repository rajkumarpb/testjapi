package javapi.cli;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class Classpaths {

    private Classpaths() {
    }

    /**
     * Resolve a runtime classpath. If {@code explicit} is given it is expanded
     * (any directory entries are replaced by the jars inside). Otherwise the
     * Gradle installDist layout {@code build/install/<project>/lib} is located,
     * falling back to {@code build/libs}.
     */
    static String resolve(String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return expand(explicit);
        }
        Path install = Path.of("build", "install");
        if (Files.isDirectory(install)) {
            try (var stream = Files.list(install)) {
                for (Path project : stream.sorted().toList()) {
                    Path lib = project.resolve("lib");
                    if (Files.isDirectory(lib)) {
                        String jars = jarsInDir(lib);
                        if (!jars.isEmpty()) {
                            return jars;
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Path libs = Path.of("build", "libs");
        if (Files.isDirectory(libs)) {
            String jars = jarsInDir(libs);
            if (!jars.isEmpty()) {
                return jars;
            }
        }
        throw new CliException("No classpath found. Pass --cp <classpath> or run from a project with "
                + "build/install/<project>/lib or build/libs populated.");
    }

    /** Assemble a classpath of {@code outDir} (freshly compiled classes) plus runtime jars. */
    static String withOutput(Path outDir, String runtimeClasspath) {
        List<String> entries = new ArrayList<>();
        entries.add(outDir.toAbsolutePath().toString());
        for (String entry : runtimeClasspath.split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                entries.add(entry);
            }
        }
        return String.join(File.pathSeparator, entries);
    }

    private static String expand(String classpath) {
        StringBuilder sb = new StringBuilder();
        for (String entry : classpath.split(File.pathSeparator)) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Path p = Path.of(trimmed);
            if (Files.isDirectory(p)) {
                append(sb, jarsInDir(p));
            } else {
                if (sb.length() > 0) {
                    sb.append(File.pathSeparator);
                }
                sb.append(p.toAbsolutePath());
            }
        }
        return sb.toString();
    }

    private static String jarsInDir(Path dir) {
        StringBuilder sb = new StringBuilder();
        try (var stream = Files.list(dir)) {
            List<Path> jars = stream
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .toList();
            for (Path jar : jars) {
                if (sb.length() > 0) {
                    sb.append(File.pathSeparator);
                }
                sb.append(jar.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String value) {
        if (value.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(File.pathSeparator);
        }
        sb.append(value);
    }
}
