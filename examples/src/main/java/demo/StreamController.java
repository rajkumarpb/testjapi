package demo;

import javapi.annotations.get;
import javapi.sse.SseEmitter;

public class StreamController {

    @get("/stream")
    public SseEmitter stream() {
        SseEmitter emitter = SseEmitter.create();
        Thread.ofVirtual().start(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    Thread.sleep(200);
                    emitter.event("tick", "value-" + i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }
}
