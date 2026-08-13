package javapi.websocket;

public interface WebSocketEndpoint {

    void onOpen(WebSocketSession session);

    void onMessage(WebSocketSession session, String message);

    default void onBinary(WebSocketSession session, byte[] data) {
    }

    default void onClose(WebSocketSession session, int status, String reason) {
    }

    default void onError(WebSocketSession session, Throwable error) {
    }
}
