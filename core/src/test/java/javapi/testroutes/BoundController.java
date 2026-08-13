package javapi.testroutes;

import java.util.Map;
import javapi.annotations.Body;
import javapi.annotations.Get;
import javapi.annotations.Optional;
import javapi.annotations.Path;
import javapi.annotations.Post;
import javapi.annotations.Query;

public class BoundController {

    @Get("/bound/:id")
    public Map<String, Object> get(@Path int id, @Query("q") @Optional String q) {
        return Map.of("id", id, "q", q == null ? "" : q);
    }

    @Post("/bound")
    public Map<String, Object> create(@Body BoundItem item) {
        return Map.of("name", item.name(), "qty", item.qty());
    }
}
