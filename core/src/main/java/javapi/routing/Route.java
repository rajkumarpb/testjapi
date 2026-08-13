package javapi.routing;

import java.util.EnumSet;
import java.util.Set;
import javapi.annotations.HttpMethod;

public record Route(
        Set<HttpMethod> methods,
        String path,
        PathMatcher matcher,
        Handler handler,
        int paramCount,
        EndpointMeta meta,
        ExecutionMode execution) {

    public Route {
        methods = methods.isEmpty()
                ? EnumSet.allOf(HttpMethod.class)
                : Set.copyOf(methods);
    }

    public static Route of(Set<HttpMethod> methods, String path, Handler handler) {
        return of(methods, path, handler, null, ExecutionMode.AUTO);
    }

    public static Route of(Set<HttpMethod> methods, String path, Handler handler, EndpointMeta meta) {
        return of(methods, path, handler, meta, ExecutionMode.AUTO);
    }

    public static Route of(Set<HttpMethod> methods, String path, Handler handler, EndpointMeta meta,
            ExecutionMode execution) {
        return new Route(methods, path, PathMatcher.compile(path), handler, countParams(path), meta, execution);
    }

    public boolean allows(HttpMethod method) {
        return methods.contains(method);
    }

    private static int countParams(String path) {
        int count = 0;
        int index = 0;
        while (index < path.length()) {
            int colon = path.indexOf(':', index);
            if (colon < 0) {
                break;
            }
            count++;
            index = colon + 1;
        }
        return count;
    }
}
