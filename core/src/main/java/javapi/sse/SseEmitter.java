package javapi.sse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javapi.json.Json;

public final class SseEmitter {

    @FunctionalInterface
    public interface Sink {
        void emit(String text);
    }

    private final Object lock = new Object();
    private final List<String> pending = new ArrayList<>();
    private Sink sink;
    private Runnable onClose;
    private boolean attached;
    private boolean closed;

    private SseEmitter() {
    }

    public static SseEmitter create() {
        return new SseEmitter();
    }

    public void send(String data) {
        emit("data: " + data + "\n\n");
    }

    public void send(Map<String, Object> json) {
        send(Json.write(json));
    }

    public void event(String name, String data) {
        emit("event: " + name + "\ndata: " + data + "\n\n");
    }

    public void comment(String text) {
        emit(": " + text + "\n");
    }

    public void complete() {
        Runnable onClose = null;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            onClose = this.onClose;
        }
        if (onClose != null) {
            onClose.run();
        }
    }

    public void attach(Sink sink, Runnable onClose) {
        List<String> queued;
        synchronized (lock) {
            if (attached) {
                throw new IllegalStateException("SseEmitter already attached");
            }
            attached = true;
            this.sink = sink;
            this.onClose = onClose;
            queued = closed ? List.of() : new ArrayList<>(pending);
            pending.clear();
        }
        for (String text : queued) {
            sink.emit(text);
        }
        if (closed) {
            onClose.run();
        }
    }

    private void emit(String text) {
        Sink target;
        synchronized (lock) {
            if (closed) {
                return;
            }
            target = attached ? sink : null;
            if (target == null) {
                pending.add(text);
                return;
            }
        }
        target.emit(text);
    }
}
