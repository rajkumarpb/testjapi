package javapi.websocket;

public interface WebSocketSession {

    void send(String message);

    void send(byte[] data);

    void close();

    void close(int status, String reason);

    boolean isOpen();
}
