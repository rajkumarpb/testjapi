package javapi.execroutes;

import java.util.Map;
import javapi.annotations.blocking;
import javapi.annotations.eventloop;
import javapi.annotations.get;
import javapi.annotations.route;

@route("/exec")
public class ExecController {

    @get("/fast")
    @eventloop
    public Map<String, String> fast() {
        return Map.of("mode", "eventloop");
    }

    @get("/slow")
    @blocking
    public Map<String, String> slow() {
        return Map.of("mode", "blocking");
    }

    @get("/auto")
    public Map<String, String> auto() {
        return Map.of("mode", "auto");
    }
}
