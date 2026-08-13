package javapi.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class RequestTest {

    private Request build(String query, String cookieHeader) {
        return Request.builder()
                .method("GET")
                .path("/x")
                .query(query)
                .headers(java.util.Map.of(
                        "User-Agent", "test",
                        "X-Request-Id", "abc"))
                .cookieHeader(cookieHeader)
                .body("")
                .build();
    }

    @Test
    void queryParamsAreParsedAndDecoded() {
        Request request = build("name=hello+world&n=%E2%9C%93&flag", "");
        assertEquals("hello world", request.queryParam("name"));
        assertEquals("\u2713", request.queryParam("n"));
        assertEquals("", request.queryParam("flag"));
        assertEquals(3, request.queryParams().size());
    }

    @Test
    void emptyQueryYieldsNoParams() {
        assertEquals(0, build("", "").queryParams().size());
    }

    @Test
    void headerLookupIsCaseInsensitive() {
        Request request = build("", "");
        assertEquals("test", request.header("user-agent"));
        assertEquals("test", request.header("User-Agent"));
        assertEquals("abc", request.header("x-request-id"));
    }

    @Test
    void cookiesAreParsed() {
        Request request = build("", "session=abc; theme=dark");
        assertEquals("abc", request.cookie("session"));
        assertEquals("dark", request.cookie("theme"));
    }

    @Test
    void pathParamsAreExposed() {
        Request request = Request.builder()
                .method("GET")
                .path("/items/42")
                .pathParams(java.util.Map.of("id", "42"))
                .build();
        assertEquals("42", request.pathParam("id"));
    }
}
