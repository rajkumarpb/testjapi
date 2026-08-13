package javapi.testroutes;

import java.util.Map;
import javapi.annotations.body;
import javapi.annotations.get;
import javapi.annotations.optional;
import javapi.annotations.path;
import javapi.annotations.post;
import javapi.annotations.query;

public class BoundController {

    @get("/bound/:id")
    public Map<String, Object> get(@path int id, @query("q") @optional String q) {
        return Map.of("id", id, "q", q == null ? "" : q);
    }

    @post("/bound")
    public Map<String, Object> create(@body BoundItem item) {
        return Map.of("name", item.name(), "qty", item.qty());
    }
}
