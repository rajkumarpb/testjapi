package javapi.routing;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javapi.annotations.HttpMethod;
import javapi.middleware.Middleware;
import javapi.request.ExceptionHandler;
import javapi.request.ExceptionMapper;
import javapi.request.Response;
import javapi.websocket.WebSocketEndpoint;

public class Router {

    private final List<Route> routes = new CopyOnWriteArrayList<>();
    /**
     * Exact-path index over parameterless routes (paramCount == 0), so a
     * request for a static path is an O(1) hash lookup instead of a linear
     * regex scan over every route. Semantics are identical to the linear scan:
     * static routes sort before parameterised ones, and among static routes the
     * first registered route that allows the method wins.
     */
    private final Map<String, List<Route>> staticRoutes = new ConcurrentHashMap<>();
    private final List<Middleware> middleware = new CopyOnWriteArrayList<>();
    private final Map<Class<? extends Throwable>, ExceptionHandler> exceptionHandlers = new ConcurrentHashMap<>();
    private final Map<String, WebSocketEndpoint> webSockets = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();

    public Router register(HttpMethod method, String path, Handler handler) {
        return register(Set.of(method), path, handler);
    }

    public Router register(Set<HttpMethod> methods, String path, Handler handler) {
        return register(methods, path, handler, null, ExecutionMode.AUTO);
    }

    public Router register(Set<HttpMethod> methods, String path, Handler handler, EndpointMeta meta) {
        return register(methods, path, handler, meta, ExecutionMode.AUTO);
    }

    public Router register(Set<HttpMethod> methods, String path, Handler handler, EndpointMeta meta,
            ExecutionMode execution) {
        Route route = Route.of(methods, path, handler, meta, execution);
        routes.add(route);
        routes.sort(Comparator.comparingInt(Route::paramCount));
        if (route.paramCount() == 0) {
            staticRoutes.computeIfAbsent(PathMatcher.normalize(route.path()), p -> new CopyOnWriteArrayList<>())
                    .add(route);
        }
        version.incrementAndGet();
        return this;
    }

    public Router use(Middleware middleware) {
        this.middleware.add(middleware);
        return this;
    }

    public Router registerWs(String path, WebSocketEndpoint endpoint) {
        String normalized = path.startsWith("/") ? path : "/" + path;
        webSockets.put(normalized, endpoint);
        version.incrementAndGet();
        return this;
    }

    public Router exception(Class<? extends Throwable> type, ExceptionHandler handler) {
        exceptionHandlers.put(type, handler);
        return this;
    }

    public RouteMatch match(HttpMethod method, String path) {
        String normalized = PathMatcher.normalize(path);
        Route fast = staticMatch(method, normalized);
        if (fast != null) {
            return new RouteMatch(fast, Map.of());
        }
        for (Route route : routes) {
            if (route.paramCount() == 0) {
                continue;
            }
            Map<String, String> params = route.matcher().match(normalized);
            if (params != null && route.allows(method)) {
                return new RouteMatch(route, params);
            }
        }
        return null;
    }

    public Set<HttpMethod> allowedMethods(String path) {
        String normalized = PathMatcher.normalize(path);
        EnumSet<HttpMethod> allowed = EnumSet.noneOf(HttpMethod.class);
        List<Route> staticCandidates = staticRoutes.get(normalized);
        if (staticCandidates != null) {
            for (Route route : staticCandidates) {
                allowed.addAll(route.methods());
            }
        }
        for (Route route : routes) {
            if (route.paramCount() == 0) {
                continue;
            }
            if (route.matcher().match(normalized) != null) {
                allowed.addAll(route.methods());
            }
        }
        return allowed;
    }

    private Route staticMatch(HttpMethod method, String path) {
        List<Route> candidates = staticRoutes.get(path);
        if (candidates == null) {
            return null;
        }
        for (Route route : candidates) {
            if (route.allows(method)) {
                return route;
            }
        }
        return null;
    }

    public List<Route> routes() {
        return List.copyOf(routes);
    }

    public List<Middleware> middleware() {
        return List.copyOf(middleware);
    }

    public WebSocketEndpoint wsHandler(String path) {
        return webSockets.get(path);
    }

    public Response mapError(Throwable throwable) {
        ExceptionHandler handler = findExceptionHandler(throwable.getClass());
        if (handler != null) {
            try {
                return handler.handle(throwable);
            } catch (Throwable secondary) {
                return ExceptionMapper.map(secondary);
            }
        }
        return ExceptionMapper.map(throwable);
    }

    private ExceptionHandler findExceptionHandler(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            ExceptionHandler direct = exceptionHandlers.get(current);
            if (direct != null) {
                return direct;
            }
            for (Class<?> iface : current.getInterfaces()) {
                ExceptionHandler handler = exceptionHandlers.get(iface);
                if (handler != null) {
                    return handler;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public long version() {
        return version.get();
    }
}
