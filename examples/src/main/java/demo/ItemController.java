package demo;

import java.util.List;
import java.util.Map;
import javapi.annotations.body;
import javapi.annotations.depends;
import javapi.annotations.exception;
import javapi.annotations.get;
import javapi.annotations.header;
import javapi.annotations.optional;
import javapi.annotations.path;
import javapi.annotations.post;
import javapi.annotations.query;
import javapi.annotations.route;
import javapi.request.HttpException;
import javapi.request.Response;

@route("/items")
public class ItemController {

    @get("/hello")
    public Map<String, Object> hello(@depends Greeter greeter, @query("name") String name) {
        return Map.of("greeting", greeter.greet(name));
    }

    @get("/missing")
    public String missing() {
        throw new HttpException(404, "Item not found");
    }

    @get("/status")
    public Response status() {
        return Response.status(202)
                .withBody(Map.of("accepted", true))
                .withHeader("X-Request-Id", "demo-123");
    }

    @get("/boom")
    public String boom() {
        throw new IllegalStateException("boom");
    }

    @exception(IllegalStateException.class)
    public Response onIllegalState(IllegalStateException error) {
        return Response.of(500, Map.of("detail", error.getMessage(), "error", "illegal_state"));
    }

    @get("/")
    public Map<String, Object> list(@query("limit") int limit, @query("q") @optional String q) {
        return Map.of("items", List.of("alpha", "beta"), "limit", limit, "q", q == null ? "" : q);
    }

    @get("/:itemId")
    public Map<String, Object> item(@path int itemId, @header("user-agent") @optional String userAgent) {
        return Map.of("itemId", itemId, "userAgent", userAgent == null ? "" : userAgent);
    }

    @post
    public Map<String, Object> create(@body Item item) {
        return Map.of("created", item);
    }
}
