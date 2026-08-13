package demo;

import javapi.annotations.component;

@component
public class EnglishGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "Hello " + name;
    }
}
