package javapi.execroutes;

import java.util.Map;
import javapi.annotations.Blocking;
import javapi.annotations.EventLoop;
import javapi.annotations.Get;
import javapi.annotations.Route;

@Route("/exec")
public class ExecController {

    @Get("/fast")
    @EventLoop
    public Map<String, String> fast() {
        return Map.of("mode", "eventloop");
    }

    @Get("/slow")
    @Blocking
    public Map<String, String> slow() {
        return Map.of("mode", "blocking");
    }

    @Get("/auto")
    public Map<String, String> auto() {
        return Map.of("mode", "auto");
    }
}
