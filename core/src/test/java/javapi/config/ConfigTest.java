package javapi.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfigTest {

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("javapi.server.port");
        System.clearProperty("javapi.port");
        System.clearProperty("javapi.profile");
    }

    @Test
    void baseFileValuesOverrideDefaults() {
        Config config = Config.load(getClass().getClassLoader());
        assertEquals(8081, config.getInt("server.port", 8080));
        assertEquals("127.0.0.9", config.get("server.host"));
    }

    @Test
    void codeConfigBeatsFileValues() {
        Config config = Config.load(getClass().getClassLoader()).with("server.port", "9090");
        assertEquals(9090, config.getInt("server.port", 8080));
    }

    @Test
    void systemPropertyBeatsFileValues() {
        System.setProperty("javapi.server.port", "7070");
        Config config = Config.load(getClass().getClassLoader());
        assertEquals(7070, config.getInt("server.port", 8080));
    }

    @Test
    void relaxedBindingMapsDottedKeyToJavapiProperty() {
        System.setProperty("javapi.port", "6060");
        Config config = Config.load(getClass().getClassLoader());
        assertEquals(6060, config.getInt("server.port", 8080));
    }

    @Test
    void profileOverlaysBaseValues() {
        System.setProperty("javapi.profile", "dev");
        Config config = Config.load(getClass().getClassLoader());
        assertEquals("127.0.0.2", config.get("server.host"));
        assertEquals(8081, config.getInt("server.port", 8080));
    }

    @Test
    void unknownKeyReturnsNullOrDefault() {
        Config config = Config.load(getClass().getClassLoader());
        assertNull(config.get("server.unknown"));
        assertEquals(1234, config.getInt("server.unknown", 1234));
    }

    @Test
    void booleanValuesAreRelaxed() {
        Config config = Config.load(getClass().getClassLoader())
                .with("server.logRequests", "on");
        assertEquals(true, config.getBoolean("server.logRequests", false));
    }
}
