package javapi.ditestroutes;

import java.util.Map;
import javapi.annotations.Depends;
import javapi.annotations.Get;
import javapi.annotations.Inject;
import javapi.annotations.Query;
import javapi.annotations.Route;

@Route("/di")
public class DiController {

    private final Counter counter;

    @Inject
    public DiController(Counter counter) {
        this.counter = counter;
    }

    @Get("/greet")
    public Map<String, Object> greet(@Depends Greeter greeter, @Query("name") String name) {
        return Map.of("greeting", greeter.greet(name), "counter", counter.describe());
    }

    @Get("/scoped")
    public String scoped(@Depends RequestScoped scoped) {
        return scoped.id();
    }
}
