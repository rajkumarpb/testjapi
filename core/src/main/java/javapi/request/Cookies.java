package javapi.request;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Cookies {

    private Cookies() {
    }

    public static Map<String, String> parse(String cookieHeader) {
        Map<String, String> result = new LinkedHashMap<>();
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return result;
        }
        for (String part : cookieHeader.split(";")) {
            String pair = part.trim();
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            result.putIfAbsent(key, value);
        }
        return result;
    }
}
