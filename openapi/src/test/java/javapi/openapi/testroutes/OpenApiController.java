package javapi.openapi.testroutes;

import java.util.Map;
import javapi.annotations.Body;
import javapi.annotations.Get;
import javapi.annotations.Optional;
import javapi.annotations.Path;
import javapi.annotations.Post;
import javapi.annotations.Query;
import javapi.annotations.Route;

@Route("/api")
public class OpenApiController {

    @Get("/hello")
    public String hello(@Query("name") @Optional String name) {
        return "hi " + (name == null ? "" : name);
    }

    @Get("/:id")
    public Map<String, Object> get(@Path int id) {
        return Map.of("id", id);
    }

    @Post("/create")
    public Map<String, Object> create(@Body Thing thing) {
        return Map.of("ok", true, "name", thing.name());
    }
}
