package javapi.openapi.testroutes;

import java.util.Map;
import javapi.annotations.body;
import javapi.annotations.get;
import javapi.annotations.optional;
import javapi.annotations.path;
import javapi.annotations.post;
import javapi.annotations.query;
import javapi.annotations.route;

@route("/api")
public class OpenApiController {

    @get("/hello")
    public String hello(@query("name") @optional String name) {
        return "hi " + (name == null ? "" : name);
    }

    @get("/:id")
    public Map<String, Object> get(@path int id) {
        return Map.of("id", id);
    }

    @post("/create")
    public Map<String, Object> create(@body Thing thing) {
        return Map.of("ok", true, "name", thing.name());
    }
}
