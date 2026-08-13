package javapi.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import javapi.annotations.HttpMethod;

class ExecutionModeTest {

    @Test
    void eventLoopAndBlockingAnnotationsSetExecutionMode() {
        Router router = RouteScanner.scan(
                new Router(), "javapi.execroutes", getClass().getClassLoader());
        assertEquals(ExecutionMode.EVENT_LOOP,
                router.match(HttpMethod.GET, "/exec/fast").route().execution());
        assertEquals(ExecutionMode.BLOCKING,
                router.match(HttpMethod.GET, "/exec/slow").route().execution());
        assertEquals(ExecutionMode.AUTO,
                router.match(HttpMethod.GET, "/exec/auto").route().execution());
    }

    @Test
    void bothAnnotationsThrowOnScan() {
        assertThrows(IllegalStateException.class, () -> RouteScanner.scan(
                new Router(), "javapi.bothroutes", getClass().getClassLoader()));
    }
}
