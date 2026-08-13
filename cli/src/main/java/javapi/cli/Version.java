package javapi.cli;

public final class Version {

    private static final String VERSION = read();

    private Version() {
    }

    public static String get() {
        return VERSION;
    }

    private static String read() {
        try (var in = Version.class.getResourceAsStream("version.txt")) {
            if (in == null) {
                return "dev";
            }
            String value = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? "dev" : value;
        } catch (Exception e) {
            return "dev";
        }
    }
}
