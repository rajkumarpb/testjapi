package javapi.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliTest {

    @Test
    void versionResourceIsExpandedByGradle() {
        assertTrue(Version.get().matches("\\d+\\.\\d+\\.\\d+"), "version: " + Version.get());
    }

    @Test
    void versionCommandPrintsVersion() {
        PrintStream original = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            assertEquals(0, Main.run(new String[] {"version"}));
        } finally {
            System.setOut(original);
        }
        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.startsWith("javapi "), "printed: " + printed);
        assertTrue(printed.contains(Version.get()));
    }

    @Test
    void unknownCommandExitsWithError() {
        PrintStream original = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            assertEquals(2, Main.run(new String[] {"bogus"}));
        } finally {
            System.setErr(original);
        }
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Unknown command"));
    }

    @Test
    void positionalAndOptionsAreParsed() {
        ArgOptions options = new ArgOptions(List.of("demo.App", "--cp", "a.jar;b.jar", "--jvm", "-Xmx1g"));
        assertEquals("demo.App", options.positional());
        assertEquals("a.jar;b.jar", options.opt("--cp"));
        assertEquals(List.of("-Xmx1g"), options.collect("--jvm"));
    }

    @Test
    void missingOptionDefaults() {
        ArgOptions options = new ArgOptions(List.of("main"));
        assertEquals(64, options.optInt("--concurrency", 64));
        assertEquals(null, options.opt("--cp"));
    }

    @Test
    void invalidIntOptionThrows() {
        ArgOptions options = new ArgOptions(List.of("main", "--requests", "lots"));
        assertThrows(CliException.class, () -> options.optInt("--requests", 10));
    }

    @Test
    void captureTargetsDefaultsToRoot() {
        assertEquals(List.of("http://localhost:8080/"), Commands.captureTargets(null, 8080));
        assertEquals(List.of("http://localhost:8000/"), Commands.captureTargets("", 8000));
    }

    @Test
    void captureTargetsExpandsPathsOnThePort() {
        assertEquals(List.of(
                "http://localhost:8000/",
                "http://localhost:8000/db/items",
                "http://localhost:8000/db/items/1"),
                Commands.captureTargets("/, /db/items, /db/items/1", 8000));
    }

    @Test
    void hasJsonConfigDetectsCapturedFiles() throws Exception {
        Path dir = Files.createTempDirectory("javapi-native-config");
        try {
            assertEquals(false, Commands.hasJsonConfig(dir));
            Files.writeString(dir.resolve("reflect-config.json"), "[]");
            assertEquals(true, Commands.hasJsonConfig(dir));
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    void resolveUsesBuildOutputOrThrows() {
        try {
            String resolved = Classpaths.resolve(null);
            assertTrue(resolved.contains("jar"), "resolved: " + resolved);
        } catch (CliException e) {
            // no build output under the current working directory — the error path is expected
        }
    }

    @Test
    void explicitClasspathWithDirectoryIsExpanded() throws Exception {
        Path dir = Files.createTempDirectory("javapi-jars");
        Files.writeString(dir.resolve("a.jar"), "x");
        Files.writeString(dir.resolve("b.jar"), "y");
        try {
            String expanded = Classpaths.resolve(dir.toString());
            assertTrue(expanded.contains("a.jar"), "expanded: " + expanded);
            assertTrue(expanded.contains("b.jar"), "expanded: " + expanded);
            assertEquals(2, expanded.split(java.io.File.pathSeparator).length, "expanded: " + expanded);
        } finally {
            deleteTree(dir);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
