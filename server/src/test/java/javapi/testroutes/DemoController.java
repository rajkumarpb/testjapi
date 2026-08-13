package javapi.testroutes;

import java.util.Map;
import javapi.annotations.HttpMethod;
import javapi.annotations.body;
import javapi.annotations.get;
import javapi.annotations.header;
import javapi.annotations.optional;
import javapi.annotations.path;
import javapi.annotations.post;
import javapi.annotations.query;
import javapi.annotations.route;
import javapi.request.BackgroundTasks;
import javapi.request.HttpException;
import javapi.request.Response;

@route("/items")
public class DemoController {

    public static int backgroundRuns = 0;

    @get("/")
    public Map<String, Object> list(@query("limit") @optional Integer limit) {
        return Map.of("ok", true, "limit", limit == null ? 0 : limit);
    }

    @get("/:itemId")
    public Map<String, Object> item(@path int itemId, @header("user-agent") @optional String userAgent) {
        return Map.of("itemId", itemId, "userAgent", userAgent == null ? "" : userAgent);
    }

    @post
    public Map<String, Object> create(@body Item item) {
        return Map.of("created", true, "name", item.name(), "qty", item.qty());
    }

    @get("/search")
    public Map<String, Object> search(@query("q") String q) {
        return Map.of("q", q);
    }

    @route(value = "/ping", methods = {HttpMethod.GET, HttpMethod.HEAD})
    public Map<String, String> ping() {
        return Map.of("pong", "ok");
    }

    @post("/custom")
    public Response custom() {
        return Response.status(201)
                .withBody(Map.of("created", true))
                .withHeader("X-Created-By", "test");
    }

    @get("/throw-404")
    public String notFound() {
        throw new HttpException(404, "No such item");
    }

    @get("/throw-500")
    public String boom() {
        throw new IllegalStateException("kaboom");
    }

    @get("/background")
    public Response background() {
        return Response.ok(Map.of("done", true))
                .withBackgroundTasks(BackgroundTasks.of(() -> backgroundRuns++));
    }
}
