package javapi.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class Config {

    private static volatile Config shared;

    private final Map<String, String> code;
    private final Properties base;
    private final Properties profile;

    private Config(Map<String, String> code, Properties base, Properties profile) {
        this.code = code;
        this.base = base;
        this.profile = profile;
    }

    public static Config load() {
        Config current = shared;
        if (current == null) {
            synchronized (Config.class) {
                current = shared;
                if (current == null) {
                    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                    if (classLoader == null) {
                        classLoader = ClassLoader.getSystemClassLoader();
                    }
                    current = load(classLoader);
                    shared = current;
                }
            }
        }
        return current;
    }

    public static Config load(ClassLoader classLoader) {
        String profileName = System.getProperty("javapi.profile", System.getenv("JAVAPI_PROFILE"));
        Properties base = loadProperties(classLoader, "application.properties");
        Properties profile = profileName == null || profileName.isBlank()
                ? new Properties()
                : loadProperties(classLoader, "application-" + profileName + ".properties");
        return new Config(Map.of(), base, profile);
    }

    private static Properties loadProperties(ClassLoader classLoader, String name) {
        Properties properties = new Properties();
        try (InputStream in = classLoader.getResourceAsStream(name)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config resource " + name, e);
        }
        return properties;
    }

    public Config with(String key, String value) {
        Map<String, String> next = new HashMap<>(code);
        next.put(key, value);
        return new Config(Map.copyOf(next), base, profile);
    }

    public String get(String key) {
        List<String> aliases = aliases(key);
        for (String alias : aliases) {
            String value = code.get(alias);
            if (value != null) {
                return value;
            }
        }
        for (String alias : aliases) {
            String value = System.getProperty(alias);
            if (value != null) {
                return value;
            }
        }
        for (String alias : aliases) {
            String value = System.getenv(normalize(alias));
            if (value != null) {
                return value;
            }
        }
        for (String alias : aliases) {
            String value = profile.getProperty(alias);
            if (value != null) {
                return value;
            }
        }
        for (String alias : aliases) {
            String value = base.getProperty(alias);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public String get(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default -> defaultValue;
        };
    }

    private static List<String> aliases(String key) {
        List<String> aliases = new ArrayList<>(3);
        aliases.add(key);
        aliases.add("javapi." + key);
        if (key.startsWith("server.")) {
            aliases.add("javapi." + key.substring("server.".length()));
        }
        return aliases;
    }

    private static String normalize(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
