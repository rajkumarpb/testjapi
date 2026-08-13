package javapi.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackgroundTasksTest {

    @Test
    void emptyIsEmpty() {
        assertTrue(BackgroundTasks.empty().isEmpty());
        assertFalse(BackgroundTasks.of(() -> {
        }).isEmpty());
    }

    @Test
    void runsInOrder() {
        List<String> order = new ArrayList<>();
        BackgroundTasks tasks = BackgroundTasks.empty()
                .add(() -> order.add("a"))
                .add(() -> order.add("b"));
        tasks.run();
        assertEquals(List.of("a", "b"), order);
    }

    @Test
    void swallowedExceptionDoesNotStopLaterTasks() {
        List<String> order = new ArrayList<>();
        BackgroundTasks tasks = BackgroundTasks.of(
                () -> {
                    throw new IllegalStateException("boom");
                },
                () -> order.add("after"));
        tasks.run();
        assertEquals(List.of("after"), order);
    }
}
