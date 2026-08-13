package javapi.testroutes;

import java.util.Map;
import javapi.annotations.HttpMethod;
import javapi.annotations.get;
import javapi.annotations.post;
import javapi.annotations.route;

@route("/items")
public class DemoController {

    @get("/")
    public Map<String, Object> list() {
        return Map.of("ok", true);
    }

    @get("/:itemId")
    public Map<String, String> item() {
        return Map.of("itemId", "x");
    }

    @post
    public Map<String, Boolean> create() {
        return Map.of("created", true);
    }

    @route(value = "/ping", methods = {HttpMethod.GET, HttpMethod.HEAD})
    public Map<String, String> ping() {
        return Map.of("pong", "ok");
    }
}
