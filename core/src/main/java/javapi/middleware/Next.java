package javapi.middleware;

import javapi.request.Request;

@FunctionalInterface
public interface Next {
    Object next(Request request);
}
