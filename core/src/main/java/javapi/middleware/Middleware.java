package javapi.middleware;

import javapi.request.Request;

@FunctionalInterface
public interface Middleware {
    Object handle(Request request, Next next);
}
