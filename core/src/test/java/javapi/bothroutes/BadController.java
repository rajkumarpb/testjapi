package javapi.bothroutes;

import java.util.Map;
import javapi.annotations.Blocking;
import javapi.annotations.EventLoop;
import javapi.annotations.Get;
import javapi.annotations.Route;

@Route("/bad")
public class BadController {

    @Get
    @EventLoop
    @Blocking
    public Map<String, String> bad() {
        return Map.of();
    }
}
