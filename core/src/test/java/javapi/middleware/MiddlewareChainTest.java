package javapi.middleware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import javapi.request.Request;
import javapi.request.Response;

class MiddlewareChainTest {

    @Test
    void runsInOrderToTerminal() {
        List<String> calls = new ArrayList<>();
        Next chain = MiddlewareChain.build(List.of(
                (req, next) -> {
                    calls.add("one");
                    return next.next(req);
                },
                (req, next) -> {
                    calls.add("two");
                    return next.next(req);
                }), req -> {
            calls.add("terminal");
            return Response.ok("done");
        });
        Object result = chain.next(Request.builder().method("GET").path("/").build());
        assertEquals(List.of("one", "two", "terminal"), calls);
        assertTrue(result instanceof Response);
    }

    @Test
    void shortCircuitsBeforeTerminal() {
        List<String> calls = new ArrayList<>();
        Next chain = MiddlewareChain.build(List.of(
                (req, next) -> {
                    calls.add("one");
                    return Response.of(403, "blocked");
                },
                (req, next) -> {
                    calls.add("two");
                    return next.next(req);
                }), req -> {
            calls.add("terminal");
            return Response.ok("done");
        });
        Object result = chain.next(Request.builder().method("GET").path("/").build());
        assertEquals(List.of("one"), calls);
        assertEquals(403, ((Response) result).status());
    }

    @Test
    void middlewareCanMutateRequestDownstream() {
        Next chain = MiddlewareChain.build(List.of(
                (req, next) -> next.next(Request.builder()
                        .method(req.method())
                        .path(req.path())
                        .headers(java.util.Map.of("X-Injected", "yes"))
                        .build())), req -> Response.ok(req.header("X-Injected")));
        Object result = chain.next(Request.builder().method("GET").path("/").build());
        assertEquals("yes", ((Response) result).body());
    }

    @Test
    void emptyChainDelegatesStraightToTerminal() {
        boolean[] called = { false };
        Next chain = MiddlewareChain.build(List.of(), req -> {
            called[0] = true;
            return Response.ok("term");
        });
        chain.next(Request.builder().method("GET").path("/").build());
        assertTrue(called[0]);
    }
}
