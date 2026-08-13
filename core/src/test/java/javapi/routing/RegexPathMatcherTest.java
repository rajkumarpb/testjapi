package javapi.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegexPathMatcherTest {

    @Test
    void rootMatchesOnlyRoot() {
        PathMatcher matcher = PathMatcher.compile("/");
        assertNotNull(matcher.match("/"));
        assertNull(matcher.match("/x"));
    }

    @Test
    void literalPathMatchesExactly() {
        PathMatcher matcher = PathMatcher.compile("/a/b");
        assertNotNull(matcher.match("/a/b"));
        assertNull(matcher.match("/a/x"));
        assertNull(matcher.match("/a/b/c"));
    }

    @Test
    void capturesSingleParam() {
        PathMatcher matcher = PathMatcher.compile("/items/:itemId");
        assertEquals(Map.of("itemId", "42"), matcher.match("/items/42"));
        assertNull(matcher.match("/items/42/sub"));
        assertNull(matcher.match("/other/42"));
    }

    @Test
    void capturesMultipleParamsByPosition() {
        PathMatcher matcher = PathMatcher.compile("/items/:itemId/comments/:commentId");
        assertEquals(
                Map.of("itemId", "42", "commentId", "7"),
                matcher.match("/items/42/comments/7"));
    }

    @Test
    void paramDoesNotMatchEmptySegment() {
        PathMatcher matcher = PathMatcher.compile("/items/:itemId");
        assertNull(matcher.match("/items/"));
    }

    @Test
    void trailingSlashIsForgiven() {
        PathMatcher matcher = PathMatcher.compile("/items");
        assertNotNull(matcher.match("/items/"));
    }

    @Test
    void templateTrailingSlashIsNormalized() {
        PathMatcher matcher = PathMatcher.compile("/items/");
        assertNotNull(matcher.match("/items"));
    }

    @Test
    void emptyParamNameRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> PathMatcher.compile("/items/:"));
    }
}
