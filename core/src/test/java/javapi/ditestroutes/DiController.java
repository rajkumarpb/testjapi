package javapi.ditestroutes;

import java.util.Map;
import javapi.annotations.depends;
import javapi.annotations.get;
import javapi.annotations.inject;
import javapi.annotations.query;
import javapi.annotations.route;

@route("/di")
public class DiController {

    private final Counter counter;

    @inject
    public DiController(Counter counter) {
        this.counter = counter;
    }

    @get("/greet")
    public Map<String, Object> greet(@depends Greeter greeter, @query("name") String name) {
        return Map.of("greeting", greeter.greet(name), "counter", counter.describe());
    }

    @get("/scoped")
    public String scoped(@depends RequestScoped scoped) {
        return scoped.id();
    }
}
