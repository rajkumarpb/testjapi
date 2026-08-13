package javapi.middleware;

import java.util.List;
import javapi.request.Request;

public final class MiddlewareChain {

    private MiddlewareChain() {
    }

    public static Next build(List<Middleware> middleware, Next terminal) {
        Next next = terminal;
        for (int i = middleware.size() - 1; i >= 0; i--) {
            Middleware current = middleware.get(i);
            Next inner = next;
            next = request -> current.handle(request, inner);
        }
        return next;
    }
}
