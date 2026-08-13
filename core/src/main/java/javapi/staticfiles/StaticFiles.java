package javapi.staticfiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javapi.middleware.Middleware;
import javapi.middleware.Next;
import javapi.request.Request;
import javapi.request.Response;

public final class StaticFiles implements Middleware {

    private static final Map<String, String> CONTENT_TYPES = contentTypes();

    private final String prefix;
    private final Path directory;
    private final ClassLoader classLoader;

    private StaticFiles(String prefix, Path directory, ClassLoader classLoader) {
        this.prefix = normalizePrefix(prefix);
        this.directory = directory;
        this.classLoader = classLoader;
    }

    public static StaticFiles fromDirectory(String prefix, Path directory) {
        return new StaticFiles(prefix, directory, null);
    }

    public static StaticFiles fromClasspath(String prefix, ClassLoader classLoader) {
        return new StaticFiles(prefix, null, classLoader);
    }

    @Override
    public Object handle(Request request, Next next) {
        String path = request.path();
        if (!path.startsWith(prefix)) {
            return next.next(request);
        }
        String relative = path.substring(prefix.length());
        if (relative.isEmpty() || "/".equals(relative)) {
            relative = "/index.html";
        }
        byte[] content = read(relative);
        if (content == null) {
            return next.next(request);
        }
        return Response.of(200, content)
                .withHeader("Content-Type", contentType(relative));
    }

    private byte[] read(String relative) {
        String cleaned = clean(relative);
        if (cleaned == null) {
            return null;
        }
        if (directory != null) {
            String relativePath = cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;
            Path file = directory.resolve(relativePath).normalize();
            if (!file.startsWith(directory.normalize()) || !Files.isRegularFile(file)) {
                return null;
            }
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                return null;
            }
        }
        String combined = prefix.equals("/") ? cleaned : prefix + cleaned;
        String resource = combined.startsWith("/") ? combined.substring(1) : combined;
        try (InputStream in = classLoader.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static String clean(String relative) {
        String normalized = relative.replace('\\', '/');
        if (normalized.contains("..")) {
            return null;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "/";
        }
        return prefix.startsWith("/") ? prefix : "/" + prefix;
    }

    private static String contentType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "application/octet-stream";
        }
        String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    private static Map<String, String> contentTypes() {
        Map<String, String> map = new HashMap<>();
        map.put("html", "text/html; charset=utf-8");
        map.put("htm", "text/html; charset=utf-8");
        map.put("css", "text/css; charset=utf-8");
        map.put("js", "application/javascript; charset=utf-8");
        map.put("mjs", "application/javascript; charset=utf-8");
        map.put("json", "application/json; charset=utf-8");
        map.put("txt", "text/plain; charset=utf-8");
        map.put("xml", "application/xml");
        map.put("csv", "text/csv; charset=utf-8");
        map.put("svg", "image/svg+xml");
        map.put("png", "image/png");
        map.put("jpg", "image/jpeg");
        map.put("jpeg", "image/jpeg");
        map.put("gif", "image/gif");
        map.put("webp", "image/webp");
        map.put("ico", "image/x-icon");
        map.put("pdf", "application/pdf");
        map.put("woff", "font/woff");
        map.put("woff2", "font/woff2");
        map.put("ttf", "font/ttf");
        map.put("mp4", "video/mp4");
        map.put("webm", "video/webm");
        return Map.copyOf(map);
    }
}
