package javapi.testroutes;

import java.util.Map;
import javapi.annotations.Get;

public class PlainController {

    @Get
    public Map<String, String> root() {
        return Map.of("hello", "world");
    }
}
