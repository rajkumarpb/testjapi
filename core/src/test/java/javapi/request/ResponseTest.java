package javapi.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResponseTest {

    @Test
    void okDefaultsTo200WithBody() {
        Response response = Response.ok(Map.of("a", 1));
        assertEquals(200, response.status());
        assertEquals(Map.of("a", 1), response.body());
        assertTrue(response.backgroundTasks().isEmpty());
    }

    @Test
    void statusWithNoBody() {
        Response response = Response.status(204);
        assertEquals(204, response.status());
        assertEquals(null, response.body());
    }

    @Test
    void ofSetsStatusAndBody() {
        Response response = Response.of(201, Map.of("created", true));
        assertEquals(201, response.status());
        assertEquals(Map.of("created", true), response.body());
    }

    @Test
    void withHeaderAppendsAndPreservesExisting() {
        Response response = Response.ok("x")
                .withHeader("X-A", "1")
                .withHeader("X-B", "2");
        assertEquals(Map.of("X-A", "1", "X-B", "2"), response.headers());
    }

    @Test
    void withStatusAndBodyCopyImmutably() {
        Response base = Response.ok(Map.of("a", 1)).withHeader("X-A", "1");
        Response changed = base.withStatus(202).withBody(Map.of("b", 2));
        assertEquals(202, changed.status());
        assertEquals(Map.of("b", 2), changed.body());
        assertEquals(200, base.status());
        assertEquals(Map.of("a", 1), base.body());
    }
}
