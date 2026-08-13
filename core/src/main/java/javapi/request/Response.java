package javapi.request;

import java.util.HashMap;
import java.util.Map;

public final class Response {

    private final int status;
    private final Object body;
    private final Map<String, String> headers;
    private final BackgroundTasks backgroundTasks;

    private Response(int status, Object body, Map<String, String> headers, BackgroundTasks backgroundTasks) {
        this.status = status;
        this.body = body;
        this.headers = headers;
        this.backgroundTasks = backgroundTasks;
    }

    public static Response ok(Object body) {
        return new Response(200, body, Map.of(), BackgroundTasks.empty());
    }

    public static Response status(int status) {
        return new Response(status, null, Map.of(), BackgroundTasks.empty());
    }

    public static Response of(int status, Object body) {
        return new Response(status, body, Map.of(), BackgroundTasks.empty());
    }

    public int status() {
        return status;
    }

    public Object body() {
        return body;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public BackgroundTasks backgroundTasks() {
        return backgroundTasks;
    }

    public Response withStatus(int status) {
        return new Response(status, body, headers, backgroundTasks);
    }

    public Response withBody(Object body) {
        return new Response(status, body, headers, backgroundTasks);
    }

    public Response withHeader(String name, String value) {
        if (headers.isEmpty()) {
            return new Response(status, body, Map.of(name, value), backgroundTasks);
        }
        Map<String, String> next = new HashMap<>(headers.size() + 1);
        next.putAll(headers);
        next.put(name, value);
        return new Response(status, body, Map.copyOf(next), backgroundTasks);
    }

    public Response withBackgroundTasks(BackgroundTasks tasks) {
        return new Response(status, body, headers, tasks);
    }
}
