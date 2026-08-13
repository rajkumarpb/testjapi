package javapi.core;

public interface Server {
    Server start();

    int port();

    void await() throws InterruptedException;

    void close();
}
