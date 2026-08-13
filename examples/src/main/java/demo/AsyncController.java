package demo;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javapi.annotations.EventLoop;
import javapi.annotations.Get;
import javapi.annotations.Route;

@Route("/async")
public class AsyncController {

    @Get("/inline")
    @EventLoop
    public Map<String, String> inline() {
        return Map.of("mode", "eventloop");
    }

    @Get("/future")
    public CompletableFuture<Map<String, String>> future() {
        return CompletableFuture.supplyAsync(() -> Map.of("async", "true"));
    }
}
