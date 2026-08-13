package javapi.routing;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class PackageScanner {

    private PackageScanner() {
    }

    public static List<Class<?>> classesInPackage(String packageName, ClassLoader classLoader) {
        String resourcePath = packageName.replace('.', '/');
        List<Class<?>> classes = new ArrayList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(resourcePath);
            while (resources.hasMoreElements()) {
                collect(resources.nextElement(), packageName, classLoader, classes);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan package " + packageName, e);
        }
        return classes;
    }

    private static void collect(URL url, String packageName, ClassLoader classLoader, List<Class<?>> classes)
            throws IOException {
        switch (url.getProtocol()) {
            case "file" -> collectFromDirectory(url, packageName, classLoader, classes);
            case "jar" -> collectFromJar(url, packageName, classLoader, classes);
            default -> {
            }
        }
    }

    private static void collectFromDirectory(URL url, String packageName, ClassLoader classLoader,
            List<Class<?>> classes) {
        try {
            File dir = new File(url.toURI());
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            for (File file : files) {
                String name = file.getName();
                if (file.isFile() && name.endsWith(".class")) {
                    classes.add(load(packageName + "." + name.substring(0, name.length() - 6), classLoader));
                }
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid package resource URL " + url, e);
        }
    }

    private static void collectFromJar(URL url, String packageName, ClassLoader classLoader, List<Class<?>> classes)
            throws IOException {
        String spec = url.toString();
        if (spec.startsWith("jar:")) {
            spec = spec.substring(4);
        }
        int bang = spec.indexOf("!/");
        if (bang < 0) {
            return;
        }
        String jarPath = spec.substring(0, bang);
        if (jarPath.startsWith("file:")) {
            jarPath = new File(URI.create(jarPath)).getPath();
        }
        String prefix = packageName.replace('.', '/');
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix + "/") || !name.endsWith(".class")) {
                    continue;
                }
                String relative = name.substring(prefix.length() + 1);
                if (relative.contains("/")) {
                    continue;
                }
                classes.add(load(name.substring(0, name.length() - 6).replace('/', '.'), classLoader));
            }
        }
    }

    private static Class<?> load(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load " + className, e);
        }
    }
}
