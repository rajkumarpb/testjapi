package javapi.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SseEmitterTest {

    @Test
    void buffersEventsBeforeAttach() {
        SseEmitter emitter = SseEmitter.create();
        emitter.send("one");
        emitter.event("tick", "two");
        List<String> received = new ArrayList<>();
        emitter.attach(received::add, () -> {
        });
        assertEquals(List.of("data: one\n\n", "event: tick\ndata: two\n\n"), received);
    }

    @Test
    void streamsEventsAfterAttach() {
        SseEmitter emitter = SseEmitter.create();
        List<String> received = new ArrayList<>();
        emitter.attach(received::add, () -> {
        });
        emitter.send("hello");
        emitter.send(Map.of("k", "v"));
        assertEquals(2, received.size());
        assertEquals("data: hello\n\n", received.get(0));
        assertTrue(received.get(1).startsWith("data: {\"k\":\"v\"}"));
    }

    @Test
    void completeRunsOnCloseCallback() {
        SseEmitter emitter = SseEmitter.create();
        boolean[] closed = { false };
        emitter.attach(text -> {
        }, () -> closed[0] = true);
        emitter.complete();
        assertTrue(closed[0]);
    }

    @Test
    void attachAfterCompleteRunsOnCloseImmediately() {
        SseEmitter emitter = SseEmitter.create();
        emitter.complete();
        boolean[] closed = { false };
        emitter.attach(text -> {
        }, () -> closed[0] = true);
        assertTrue(closed[0]);
    }

    @Test
    void doubleAttachIsRejected() {
        SseEmitter emitter = SseEmitter.create();
        emitter.attach(text -> {
        }, () -> {
        });
        assertThrows(IllegalStateException.class,
                () -> emitter.attach(text -> {
                }, () -> {
                }));
    }

    @Test
    void eventsAfterCompleteAreDropped() {
        SseEmitter emitter = SseEmitter.create();
        List<String> received = new ArrayList<>();
        emitter.attach(received::add, () -> {
        });
        emitter.complete();
        emitter.send("ignored");
        assertEquals(List.of(), received);
    }

    @Test
    void commentEmittedAsCommentFrame() {
        SseEmitter emitter = SseEmitter.create();
        List<String> received = new ArrayList<>();
        emitter.attach(received::add, () -> {
        });
        emitter.comment("keepalive");
        assertEquals(": keepalive\n", received.get(0));
    }
}
