package javapi.testroutes;

import java.util.Map;
import javapi.annotations.HttpMethod;
import javapi.annotations.Get;
import javapi.annotations.Post;
import javapi.annotations.Route;

@Route("/items")
public class DemoController {

    @Get("/")
    public Map<String, Object> list() {
        return Map.of("ok", true);
    }

    @Get("/:itemId")
    public Map<String, String> item() {
        return Map.of("itemId", "x");
    }

    @Post
    public Map<String, Boolean> create() {
        return Map.of("created", true);
    }

    @Route(value = "/ping", methods = {HttpMethod.GET, HttpMethod.HEAD})
    public Map<String, String> ping() {
        return Map.of("pong", "ok");
    }
}
