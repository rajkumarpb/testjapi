package javapi.testroutes;

import java.util.Map;
import javapi.annotations.HttpMethod;
import javapi.annotations.Body;
import javapi.annotations.Get;
import javapi.annotations.Header;
import javapi.annotations.Optional;
import javapi.annotations.Path;
import javapi.annotations.Post;
import javapi.annotations.Query;
import javapi.annotations.Route;
import javapi.request.BackgroundTasks;
import javapi.request.HttpException;
import javapi.request.Response;

@Route("/items")
public class DemoController {

    public static int backgroundRuns = 0;

    @Get("/")
    public Map<String, Object> list(@Query("limit") @Optional Integer limit) {
        return Map.of("ok", true, "limit", limit == null ? 0 : limit);
    }

    @Get("/:itemId")
    public Map<String, Object> item(@Path int itemId, @Header("user-agent") @Optional String userAgent) {
        return Map.of("itemId", itemId, "userAgent", userAgent == null ? "" : userAgent);
    }

    @Post
    public Map<String, Object> create(@Body Item item) {
        return Map.of("created", true, "name", item.name(), "qty", item.qty());
    }

    @Get("/search")
    public Map<String, Object> search(@Query("q") String q) {
        return Map.of("q", q);
    }

    @Route(value = "/ping", methods = {HttpMethod.GET, HttpMethod.HEAD})
    public Map<String, String> ping() {
        return Map.of("pong", "ok");
    }

    @Post("/custom")
    public Response custom() {
        return Response.status(201)
                .withBody(Map.of("created", true))
                .withHeader("X-Created-By", "test");
    }

    @Get("/throw-404")
    public String notFound() {
        throw new HttpException(404, "No such item");
    }

    @Get("/throw-500")
    public String boom() {
        throw new IllegalStateException("kaboom");
    }

    @Get("/background")
    public Response background() {
        return Response.ok(Map.of("done", true))
                .withBackgroundTasks(BackgroundTasks.of(() -> backgroundRuns++));
    }
}
