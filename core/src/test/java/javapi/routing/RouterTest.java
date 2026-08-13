package javapi.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Set;
import org.junit.jupiter.api.Test;
import javapi.annotations.HttpMethod;

class RouterTest {

    private final Router router = new Router();

    @Test
    void routesAllVerbs() {
        router.register(HttpMethod.GET, "/resource", r -> "get");
        router.register(HttpMethod.POST, "/resource", r -> "post");
        router.register(HttpMethod.PUT, "/resource", r -> "put");
        router.register(HttpMethod.DELETE, "/resource", r -> "delete");
        router.register(HttpMethod.PATCH, "/resource", r -> "patch");
        router.register(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/resource", r -> "head");

        assertEquals("get", router.match(HttpMethod.GET, "/resource").route().handler().handle(null));
        assertEquals("post", router.match(HttpMethod.POST, "/resource").route().handler().handle(null));
        assertEquals("put", router.match(HttpMethod.PUT, "/resource").route().handler().handle(null));
        assertEquals("delete", router.match(HttpMethod.DELETE, "/resource").route().handler().handle(null));
        assertEquals("patch", router.match(HttpMethod.PATCH, "/resource").route().handler().handle(null));
        assertEquals("head", router.match(HttpMethod.HEAD, "/resource").route().handler().handle(null));
    }

    @Test
    void literalSegmentsWinOverParamsRegardlessOfOrder() {
        router.register(HttpMethod.GET, "/items/:itemId", r -> "param");
        router.register(HttpMethod.GET, "/items/new", r -> "literal");
        assertEquals("literal", router.match(HttpMethod.GET, "/items/new").route().handler().handle(null));
        assertEquals("param", router.match(HttpMethod.GET, "/items/42").route().handler().handle(null));
    }

    @Test
    void capturesPathParams() {
        router.register(HttpMethod.GET, "/items/:itemId/comments/:commentId", r -> "x");
        RouteMatch match = router.match(HttpMethod.GET, "/items/42/comments/7");
        assertNotNull(match);
        assertEquals("42", match.pathParams().get("itemId"));
        assertEquals("7", match.pathParams().get("commentId"));
    }

    @Test
    void methodNotAllowedReportsAllowedMethods() {
        router.register(HttpMethod.GET, "/items", r -> "x");
        router.register(HttpMethod.POST, "/items", r -> "y");
        assertNull(router.match(HttpMethod.PUT, "/items"));
        assertEquals(Set.of(HttpMethod.GET, HttpMethod.POST), router.allowedMethods("/items"));
        assertTrue(router.allowedMethods("/unknown").isEmpty());
    }

    @Test
    void unknownPathMatchesNothing() {
        router.register(HttpMethod.GET, "/items", r -> "x");
        assertNull(router.match(HttpMethod.GET, "/nope"));
        assertTrue(router.allowedMethods("/nope").isEmpty());
    }

    @Test
    void staticFastPathHonoursTrailingSlashNormalization() {
        router.register(HttpMethod.GET, "/a/b", r -> "ab");
        router.register(HttpMethod.GET, "/a/:x", r -> "param");
        assertEquals("ab", router.match(HttpMethod.GET, "/a/b").route().handler().handle(null));
        assertEquals("ab", router.match(HttpMethod.GET, "/a/b/").route().handler().handle(null));
        assertEquals("param", router.match(HttpMethod.GET, "/a/z").route().handler().handle(null));
        assertNull(router.match(HttpMethod.GET, "/a/b/c"));
    }

    @Test
    void staticFastPathFallsThroughToParamRouteOnMethodMismatch() {
        router.register(HttpMethod.POST, "/items/new", r -> "static-post");
        router.register(HttpMethod.GET, "/items/:id", r -> "param");
        assertEquals("param", router.match(HttpMethod.GET, "/items/new").route().handler().handle(null));
        assertEquals("static-post", router.match(HttpMethod.POST, "/items/new").route().handler().handle(null));
    }

    @Test
    void staticFastPathScalesToLargeRouteTables() {
        for (int i = 1; i <= 1000; i++) {
            final int n = i;
            router.register(HttpMethod.GET, "/r" + n, r -> "route" + n);
        }
        assertEquals("route1000", router.match(HttpMethod.GET, "/r1000").route().handler().handle(null));
        assertEquals("route1", router.match(HttpMethod.GET, "/r1").route().handler().handle(null));
        assertNull(router.match(HttpMethod.GET, "/r1001"));
    }
}
