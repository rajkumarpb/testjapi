package demo;

import javapi.annotations.Component;

@Component
public class EnglishGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "Hello " + name;
    }
}
