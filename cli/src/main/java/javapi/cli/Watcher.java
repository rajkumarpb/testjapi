package javapi.cli;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Watches a source tree for {@code .java} file changes and invokes a callback
 * (debounced) once per burst of edits.
 */
final class Watcher implements AutoCloseable {

    private static final long DEBOUNCE_MS = 300;

    private final WatchService service;
    private final Consumer<List<Path>> onChange;
    private final ScheduledExecutorService debouncer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "javapi-debouncer");
                t.setDaemon(true);
                return t;
            });
    private final Thread loop;
    private volatile boolean running = true;
    private ScheduledFuture<?> pending;

    Watcher(Path root, Consumer<List<Path>> onChange) throws IOException {
        this.service = FileSystems.getDefault().newWatchService();
        this.onChange = onChange;
        registerAll(root);
        this.loop = new Thread(() -> loop(root), "javapi-watcher");
        this.loop.setDaemon(true);
        this.loop.start();
    }

    private void registerAll(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void register(Path dir) {
        try {
            dir.register(service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException e) {
            throw new CliException("Failed to watch " + dir, e);
        }
    }

    private void loop(Path root) {
        while (running) {
            WatchKey key;
            try {
                key = service.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                return;
            }
            List<Path> changed = new ArrayList<>();
            for (WatchEvent<?> event : key.pollEvents()) {
                Path dir = (Path) key.watchable();
                Path full = dir.resolve((Path) event.context());
                if (Files.isDirectory(full)) {
                    try {
                        registerAll(full);
                    } catch (IOException ignored) {
                    }
                }
                if (full.toString().endsWith(".java")) {
                    changed.add(full);
                }
            }
            key.reset();
            if (!changed.isEmpty()) {
                schedule(changed);
            }
        }
    }

    private void schedule(List<Path> changed) {
        if (pending != null) {
            pending.cancel(false);
        }
        List<Path> snapshot = List.copyOf(changed);
        pending = debouncer.schedule(() -> onChange.accept(snapshot), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        running = false;
        try {
            service.close();
        } catch (IOException ignored) {
        }
        debouncer.shutdownNow();
    }
}
