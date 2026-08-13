package javapi.routing;

import javapi.request.Request;

@FunctionalInterface
public interface Handler {
    Object handle(Request request);
}
