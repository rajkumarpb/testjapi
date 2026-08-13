package javapi.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonReadTest {

    record Item(String name, int qty, Optional<String> tag, List<String> labels) {
    }

    record Sparse(int a, String b, boolean c, List<String> d, Set<String> e, Optional<Integer> f) {
    }

    enum Color {
        RED, GREEN
    }

    @Test
    void parsesPrimitives() {
        Object parsed = Json.parse("42", Object.class);
        assertEquals(Long.class, parsed.getClass());
        assertEquals(42L, parsed);
        assertEquals(3.5, Json.parse("3.5", Object.class));
        assertEquals(Boolean.TRUE, Json.parse("true", Object.class));
        assertNull(Json.parse("null", Object.class));
        assertEquals("hi", Json.parse("\"hi\"", Object.class));
    }

    @Test
    void parsesStringEscapes() {
        assertEquals("a\nb", Json.parse("\"a\\nb\"", String.class));
        assertEquals("\u2713", Json.parse("\"\\u2713\"", String.class));
        assertEquals("q\"t", Json.parse("\"q\\\"t\"", String.class));
    }

    @Test
    void parsesArraysAndObjects() {
        Object list = Json.parse("[1,2,3]", Object.class);
        assertTrue(list instanceof List<?>);
        assertEquals(3, ((List<?>) list).size());

        Object map = Json.parse("{\"a\":1,\"b\":[true]}", Object.class);
        assertTrue(map instanceof Map<?, ?>);
        assertEquals(1L, ((Map<?, ?>) map).get("a"));
    }

    @Test
    void convertsListWithElementType() {
        List<String> result = Json.parse("[\"x\",\"y\"]", new TypeToken<List<String>>() {
        }.get());
        assertEquals(List.of("x", "y"), result);
    }

    @Test
    void convertsRecordWithAllFields() {
        Item item = Json.parse("{\"name\":\"bolt\",\"qty\":3,\"tag\":\"new\",\"labels\":[\"a\"]}",
                new TypeToken<Item>() {
                }.get());
        assertEquals("bolt", item.name());
        assertEquals(3, item.qty());
        assertEquals(Optional.of("new"), item.tag());
        assertEquals(List.of("a"), item.labels());
    }

    @Test
    void recordMissingFieldsUseDefaults() {
        Sparse sparse = Json.parse("{\"a\":1}", new TypeToken<Sparse>() {
        }.get());
        assertEquals(1, sparse.a());
        assertEquals("", sparse.b());
        assertEquals(false, sparse.c());
        assertEquals(List.of(), sparse.d());
        assertEquals(Set.of(), sparse.e());
        assertEquals(Optional.empty(), sparse.f());
    }

    @Test
    void convertsNestedMaps() {
        Map<String, Integer> result = Json.parse("{\"x\":1,\"y\":2}", new TypeToken<Map<String, Integer>>() {
        }.get());
        assertEquals(Map.of("x", 1, "y", 2), result);
    }

    @Test
    void convertsEnum() {
        Color color = Json.parse("\"red\"", new TypeToken<Color>() {
        }.get());
        assertEquals(Color.RED, color);
    }

    @Test
    void malformedJsonThrows() {
        assertThrows(JsonException.class, () -> Json.parse("{\"a\":", Object.class));
        assertThrows(JsonException.class, () -> Json.parse("tru", Object.class));
    }

    @Test
    void wrongNumberTypeThrows() {
        assertThrows(JsonException.class, () -> Json.parse("\"abc\"", new TypeToken<Integer>() {
        }.get()));
    }

    private abstract static class TypeToken<T> {
        private final java.lang.reflect.Type type;

        TypeToken() {
            this.type = ((java.lang.reflect.ParameterizedType) getClass().getGenericSuperclass())
                    .getActualTypeArguments()[0];
        }

        java.lang.reflect.Type get() {
            return type;
        }
    }
}
