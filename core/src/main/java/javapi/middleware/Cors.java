package javapi.middleware;

import java.util.List;
import javapi.request.Request;
import javapi.request.Response;

public final class Cors implements Middleware {

    private static final List<String> DEFAULT_METHODS = List.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
    private static final String WILDCARD = "*";

    private final List<String> origins;
    private final List<String> methods;
    private final List<String> headers;
    private final boolean credentials;

    private Cors(List<String> origins, List<String> methods, List<String> headers, boolean credentials) {
        this.origins = origins;
        this.methods = methods;
        this.headers = headers;
        this.credentials = credentials;
    }

    public static Cors config() {
        return new Cors(List.of(WILDCARD), DEFAULT_METHODS, List.of(WILDCARD), false);
    }

    public Cors origins(String... origins) {
        return new Cors(List.of(origins), methods, headers, credentials);
    }

    public Cors methods(String... methods) {
        return new Cors(origins, List.of(methods), headers, credentials);
    }

    public Cors headers(String... headers) {
        return new Cors(origins, methods, List.of(headers), credentials);
    }

    public Cors credentials(boolean credentials) {
        return new Cors(origins, methods, headers, credentials);
    }

    @Override
    public Object handle(Request request, Next next) {
        String origin = request.header("origin");
        boolean preflight = "OPTIONS".equalsIgnoreCase(request.method())
                && request.header("access-control-request-method") != null;
        if (preflight) {
            return applyCors(Response.status(204), origin, true);
        }
        Object result = next.next(request);
        if (!(result instanceof Response response)) {
            return result;
        }
        return applyCors(response, origin, false);
    }

    private Response applyCors(Response response, String origin, boolean preflight) {
        if (origin == null || !isAllowed(origin)) {
            return response;
        }
        Response cors = response
                .withHeader("Access-Control-Allow-Origin",
                        origins.contains(WILDCARD) && !credentials ? WILDCARD : origin)
                .withHeader("Vary", "Origin");
        if (credentials) {
            cors = cors.withHeader("Access-Control-Allow-Credentials", "true");
        }
        if (preflight) {
            cors = cors
                    .withHeader("Access-Control-Allow-Methods", String.join(", ", methods))
                    .withHeader("Access-Control-Max-Age", "3600");
            if (!headers.contains(WILDCARD)) {
                cors = cors.withHeader("Access-Control-Allow-Headers", String.join(", ", headers));
            }
        }
        return cors;
    }

    private boolean isAllowed(String origin) {
        if (origins.contains(WILDCARD)) {
            return true;
        }
        for (String allowed : origins) {
            if (allowed.equalsIgnoreCase(origin)) {
                return true;
            }
        }
        return false;
    }
}
