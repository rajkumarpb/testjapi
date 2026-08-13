package javapi.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegexPathMatcher implements PathMatcher {

    private final String template;
    private final Pattern pattern;
    private final List<String> paramNames;

    public RegexPathMatcher(String template) {
        this.template = PathMatcher.normalize(template);
        this.paramNames = new ArrayList<>();
        this.pattern = Pattern.compile(buildRegex(this.template));
    }

    @Override
    public String template() {
        return template;
    }

    @Override
    public Map<String, String> match(String path) {
        Matcher matcher = pattern.matcher(PathMatcher.normalize(path));
        if (!matcher.matches()) {
            return null;
        }
        Map<String, String> params = new HashMap<>(paramNames.size());
        for (String name : paramNames) {
            params.put(name, matcher.group(name));
        }
        return params;
    }

    private String buildRegex(String path) {
        StringBuilder regex = new StringBuilder("^");
        String[] segments = split(path);
        for (String segment : segments) {
            if (segment.startsWith(":")) {
                String name = segment.substring(1);
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Empty path parameter in " + path);
                }
                paramNames.add(name);
                regex.append("/(?<").append(name).append(">[^/]+)");
            } else {
                regex.append('/').append(Pattern.quote(segment));
            }
        }
        if (segments.length == 0) {
            regex.append('/');
        }
        return regex.append('$').toString();
    }

    private static String[] split(String path) {
        if (path.isEmpty() || "/".equals(path)) {
            return new String[0];
        }
        return path.substring(1).split("/", -1);
    }
}
