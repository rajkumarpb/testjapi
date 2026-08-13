package javapi.params;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import javapi.annotations.Body;
import javapi.annotations.Cookie;
import javapi.annotations.Header;
import javapi.annotations.Min;
import javapi.annotations.MinLength;
import javapi.annotations.MaxLength;
import javapi.annotations.Optional;
import javapi.annotations.Path;
import javapi.annotations.Query;
import javapi.request.Request;

class ParamBinderTest {

    static class Targets {

        public String item(@Path("id") int id, @Query("q") @Optional String q) {
            return id + ":" + q;
        }

        public String named(@Query String name, @Header("X-Token") String token, @Cookie("session") String session) {
            return name + ":" + token + ":" + session;
        }

        public String body(@Body Payload payload) {
            return payload.name();
        }

        public String optionalBody(@Body @Optional Payload payload) {
            return payload == null ? "none" : payload.name();
        }

        public String mandatory(@Path("id") int id, @Query("name") String name) {
            return id + ":" + name;
        }

        public String badNumber(@Path("id") int id) {
            return String.valueOf(id);
        }

        public String badEnum(@Query("level") Level level) {
            return level.name();
        }

        public String constrained(@Query("q") @MinLength(3) @MaxLength(5) String q) {
            return q;
        }

        public String constrainedInt(@Path("id") @Min(5) int id) {
            return String.valueOf(id);
        }

        public String constrainedBody(@Body Payload payload) {
            return payload.name();
        }
    }

    enum Level {
        LOW, HIGH
    }

    record Payload(@MinLength(2) String name, int qty) {
    }

    private static Method find(String name) {
        for (Method method : Targets.class.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError("no method " + name);
    }

    private static Object[] bind(String methodName, Request request) {
        return new ParamBinder(find(methodName)).bind(request);
    }

    private static Request req(String path, String query, Map<String, String> headers, String cookie, String body) {
        return Request.builder()
                .method("GET")
                .path(path)
                .pathParams(Map.of("id", "42"))
                .query(query)
                .headers(headers)
                .cookieHeader(cookie)
                .body(body)
                .build();
    }

    @Test
    void coercesScalarAndOptionalQuery() throws Exception {
        Object[] args = bind("item", req("/items/42", "q=hi", Map.of(), "", ""));
        assertEquals(42, args[0]);
        assertEquals("hi", args[1]);
    }

    @Test
    void missingOptionalParamBindsNull() throws Exception {
        Object[] args = bind("item", req("/items/42", "", Map.of(), "", ""));
        assertEquals(42, args[0]);
        assertEquals(null, args[1]);
    }

    @Test
    void missingMandatoryParamFails() throws Exception {
        RequestValidationError error = assertThrows(RequestValidationError.class,
                () -> bind("mandatory", req("/items/42", "", Map.of(), "", "")));
        assertEquals(1, error.errors().size());
        FieldError fieldError = error.errors().get(0);
        assertEquals("missing", fieldError.type());
        assertTrue(fieldError.loc().contains("name"));
    }

    @Test
    void wrongNumberFails() throws Exception {
        Request request = Request.builder()
                .method("GET")
                .path("/items/abc")
                .pathParams(Map.of("id", "abc"))
                .build();
        RequestValidationError error = assertThrows(RequestValidationError.class,
                () -> bind("badNumber", request));
        assertEquals("int_parsing", error.errors().get(0).type());
    }

    @Test
    void wrongEnumFails() throws Exception {
        RequestValidationError error = assertThrows(RequestValidationError.class,
                () -> bind("badEnum", req("/items", "level=MEDIUM", Map.of(), "", "")));
        assertEquals("enum", error.errors().get(0).type());
    }

    @Test
    void bindsFromQueryHeaderAndCookie() throws Exception {
        Object[] args = bind("named", req("/items",
                "name=neo", Map.of("X-Token", "t1"), "session=s1", ""));
        assertEquals("neo", args[0]);
        assertEquals("t1", args[1]);
        assertEquals("s1", args[2]);
    }

    @Test
    void parsesJsonBodyIntoRecord() throws Exception {
        Object[] args = bind("body", req("/items", "", Map.of(), "",
                "{\"name\":\"bolt\",\"qty\":3}"));
        Payload payload = (Payload) args[0];
        assertEquals("bolt", payload.name());
        assertEquals(3, payload.qty());
    }

    @Test
    void invalidJsonBodyFails() throws Exception {
        RequestValidationError error = assertThrows(RequestValidationError.class,
                () -> bind("body", req("/items", "", Map.of(), "", "{\"name\":")),
                "malformed JSON must produce a 422-style validation error");
        assertEquals("json_invalid", error.errors().get(0).type());
    }

    @Test
    void emptyOptionalBodyBindsNull() throws Exception {
        Object[] args = bind("optionalBody", req("/items", "", Map.of(), "", ""));
        assertEquals(null, args[0]);
    }

    @Test
    void enumCoercionIsCaseInsensitive() throws Exception {
        Object[] args = bind("badEnum", req("/items", "level=high", Map.of(), "", ""));
        assertEquals(Level.HIGH, args[0]);
    }

    @Test
    void stringConstraintViolationFails() throws Exception {
        RequestValidationError error = assertThrows(RequestValidationError.class,
                () -> bind("constrained", req("/items", "q=ab", Map.of(), "", "")));
        assertEquals("string_too_short", error.errors().get(0).type());
        assertEquals(List.of("query", "q"), error.errors().get(0).loc());
    }

    @Test
    void stringConstraintSatisfiedBinds() throws Exception {
        Object[] args = bind("constrained", req("/items", "q=abcd", Map.of(), "", ""));
        assertEquals("abcd", args[0]);
    }

    @Test
    void numericConstraintViolationFails() throws Exception {
        Request request = Request.builder()
                .method("GET")
                .path("/items/1")
                .pathParams(Map.of("id", "1"))
                .build();
        RequestValidationError error = assertThrows(RequestValidationError.class,
                () -> bind("constrainedInt", request));
        assertEquals("greater_than_equal", error.errors().get(0).type());
    }

    @Test
    void bodyRecordConstraintsEnforced() throws Exception {
        RequestValidationError error = assertThrows(RequestValidationError.class,
                () -> bind("constrainedBody", req("/items", "", Map.of(), "",
                        "{\"name\":\"a\",\"qty\":3}")));
        assertEquals("string_too_short", error.errors().get(0).type());
        assertEquals(List.of("body", "name"), error.errors().get(0).loc());
    }
}
