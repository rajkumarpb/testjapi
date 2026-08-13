package javapi.bothroutes;

import java.util.Map;
import javapi.annotations.blocking;
import javapi.annotations.eventloop;
import javapi.annotations.get;
import javapi.annotations.route;

@route("/bad")
public class BadController {

    @get
    @eventloop
    @blocking
    public Map<String, String> bad() {
        return Map.of();
    }
}
