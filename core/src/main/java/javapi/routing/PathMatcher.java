package javapi.routing;

import java.util.Map;

public interface PathMatcher {

    Map<String, String> match(String path);

    String template();

    static PathMatcher compile(String template) {
        return new RegexPathMatcher(template);
    }

    /** Normalize a path: empty becomes {@code /}, trailing slashes are stripped. */
    static String normalize(String path) {
        String p = path.isEmpty() ? "/" : path;
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
