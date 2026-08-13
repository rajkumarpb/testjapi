package demo;

import javapi.websocket.WebSocketEndpoint;
import javapi.websocket.WebSocketSession;

public class EchoEndpoint implements WebSocketEndpoint {

    @Override
    public void onOpen(WebSocketSession session) {
        session.send("welcome");
    }

    @Override
    public void onMessage(WebSocketSession session, String message) {
        session.send("echo:" + message);
    }

    @Override
    public void onClose(WebSocketSession session, int status, String reason) {
        System.out.println("[javapi] websocket closed: " + status + " " + reason);
    }
}
