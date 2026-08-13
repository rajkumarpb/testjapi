package demo;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javapi.annotations.eventloop;
import javapi.annotations.get;
import javapi.annotations.route;

@route("/async")
public class AsyncController {

    @get("/inline")
    @eventloop
    public Map<String, String> inline() {
        return Map.of("mode", "eventloop");
    }

    @get("/future")
    public CompletableFuture<Map<String, String>> future() {
        return CompletableFuture.supplyAsync(() -> Map.of("async", "true"));
    }
}
