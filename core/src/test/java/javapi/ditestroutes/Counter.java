package javapi.ditestroutes;

import javapi.annotations.Inject;

public class Counter {

    private final Greeter greeter;

    @Inject
    public Counter(Greeter greeter) {
        this.greeter = greeter;
    }

    public String describe() {
        return "counter(" + greeter.greet("x") + ")";
    }
}
