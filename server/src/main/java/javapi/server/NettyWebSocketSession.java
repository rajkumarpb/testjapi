package javapi.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import javapi.websocket.WebSocketSession;

final class NettyWebSocketSession implements WebSocketSession {

    private final Channel channel;
    private final WebSocketServerHandshaker handshaker;

    NettyWebSocketSession(Channel channel, WebSocketServerHandshaker handshaker) {
        this.channel = channel;
        this.handshaker = handshaker;
    }

    @Override
    public void send(String message) {
        channel.writeAndFlush(new TextWebSocketFrame(message));
    }

    @Override
    public void send(byte[] data) {
        channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)));
    }

    @Override
    public void close() {
        close(1000, "bye");
    }

    @Override
    public void close(int status, String reason) {
        handshaker.close(channel, new CloseWebSocketFrame(status, reason));
    }

    @Override
    public boolean isOpen() {
        return channel.isActive();
    }
}
