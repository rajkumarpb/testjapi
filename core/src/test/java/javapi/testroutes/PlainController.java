package javapi.testroutes;

import java.util.Map;
import javapi.annotations.get;

public class PlainController {

    @get
    public Map<String, String> root() {
        return Map.of("hello", "world");
    }
}
