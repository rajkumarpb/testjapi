package javapi.phase8testroutes;

import javapi.annotations.get;
import javapi.sse.SseEmitter;

public class SseController {

    @get("/stream")
    public SseEmitter stream() {
        SseEmitter emitter = SseEmitter.create();
        Thread.ofVirtual().start(() -> {
            try {
                for (int i = 1; i <= 3; i++) {
                    Thread.sleep(30);
                    emitter.send("tick-" + i);
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
