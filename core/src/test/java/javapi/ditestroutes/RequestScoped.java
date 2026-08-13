package javapi.ditestroutes;

import java.util.UUID;

public class RequestScoped implements AutoCloseable {

    public static int closes = 0;

    private final String id = UUID.randomUUID().toString();

    public String id() {
        return id;
    }

    @Override
    public void close() {
        closes++;
    }
}
