package javapi.ditestroutes;

import javapi.annotations.inject;

public class Counter {

    private final Greeter greeter;

    @inject
    public Counter(Greeter greeter) {
        this.greeter = greeter;
    }

    public String describe() {
        return "counter(" + greeter.greet("x") + ")";
    }
}
