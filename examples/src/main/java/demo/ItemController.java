package demo;

import java.util.List;
import java.util.Map;
import javapi.annotations.Body;
import javapi.annotations.Depends;
import javapi.annotations.ExceptionHandler;
import javapi.annotations.Get;
import javapi.annotations.Header;
import javapi.annotations.Optional;
import javapi.annotations.Path;
import javapi.annotations.Post;
import javapi.annotations.Query;
import javapi.annotations.Route;
import javapi.request.HttpException;
import javapi.request.Response;

@Route("/items")
public class ItemController {

    @Get("/hello")
    public Map<String, Object> hello(@Depends Greeter greeter, @Query("name") String name) {
        return Map.of("greeting", greeter.greet(name));
    }

    @Get("/missing")
    public String missing() {
        throw new HttpException(404, "Item not found");
    }

    @Get("/status")
    public Response status() {
        return Response.status(202)
                .withBody(Map.of("accepted", true))
                .withHeader("X-Request-Id", "demo-123");
    }

    @Get("/boom")
    public String boom() {
        throw new IllegalStateException("boom");
    }

    @ExceptionHandler(IllegalStateException.class)
    public Response onIllegalState(IllegalStateException error) {
        return Response.of(500, Map.of("detail", error.getMessage(), "error", "illegal_state"));
    }

    @Get("/")
    public Map<String, Object> list(@Query("limit") int limit, @Query("q") @Optional String q) {
        return Map.of("items", List.of("alpha", "beta"), "limit", limit, "q", q == null ? "" : q);
    }

    @Get("/:itemId")
    public Map<String, Object> item(@Path int itemId, @Header("user-agent") @Optional String userAgent) {
        return Map.of("itemId", itemId, "userAgent", userAgent == null ? "" : userAgent);
    }

    @Post
    public Map<String, Object> create(@Body Item item) {
        return Map.of("created", item);
    }
}
