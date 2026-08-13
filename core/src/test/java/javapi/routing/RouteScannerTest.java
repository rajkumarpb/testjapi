package javapi.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Set;
import org.junit.jupiter.api.Test;
import javapi.annotations.HttpMethod;
import javapi.di.DI;
import javapi.ditestroutes.Counter;
import javapi.ditestroutes.EnglishGreeter;
import javapi.ditestroutes.Greeter;
import javapi.ditestroutes.RequestScoped;
import javapi.request.Request;

class RouteScannerTest {

    private final Router router = RouteScanner.scan(
            new Router(), "javapi.testroutes", getClass().getClassLoader());

    @Test
    void classPrefixMergesWithMethodPaths() {
        assertNotNull(router.match(HttpMethod.GET, "/items"));
        assertNotNull(router.match(HttpMethod.GET, "/items/42"));
    }

    @Test
    void bareVerbDefaultsToClassPrefix() {
        assertNotNull(router.match(HttpMethod.POST, "/items"));
    }

    @Test
    void methodLevelRouteWithMethods() {
        assertNotNull(router.match(HttpMethod.GET, "/items/ping"));
        assertNotNull(router.match(HttpMethod.HEAD, "/items/ping"));
        assertNull(router.match(HttpMethod.POST, "/items/ping"));
        assertEquals(Set.of(HttpMethod.GET, HttpMethod.HEAD), router.allowedMethods("/items/ping"));
    }

    @Test
    void bareVerbWithoutPrefixDefaultsToRoot() {
        assertNotNull(router.match(HttpMethod.GET, "/"));
    }

    @Test
    void scannedHandlersInvokeEndpointMethods() {
        Object result = router.match(HttpMethod.GET, "/items").route().handler().handle(null);
        assertTrue(result.toString().contains("true"));
    }

    @Test
    void unknownPathIsNotFound() {
        assertNull(router.match(HttpMethod.GET, "/nope"));
        assertTrue(router.allowedMethods("/nope").isEmpty());
    }

    @Test
    void scannedHandlersBindPathAndQueryParams() {
        javapi.request.Request request = javapi.request.Request.builder()
                .method("GET")
                .path("/bound/7")
                .query("q=hi")
                .pathParams(java.util.Map.of("id", "7"))
                .build();
        Object result = router.match(HttpMethod.GET, "/bound/7").route().handler().handle(request);
        assertTrue(result.toString().contains("id=7"), "expected bound id, got: " + result);
        assertTrue(result.toString().contains("q=hi"), "expected bound q, got: " + result);
    }

    @Test
    void scannedHandlerParsesBodyRecord() {
        javapi.request.Request request = javapi.request.Request.builder()
                .method("POST")
                .path("/bound")
                .body("{\"name\":\"x\",\"qty\":2}")
                .build();
        Object result = router.match(HttpMethod.POST, "/bound").route().handler().handle(request);
        assertTrue(result.toString().contains("name=x"), "got: " + result);
        assertTrue(result.toString().contains("qty=2"), "got: " + result);
    }

    @Test
    void scannedHandlerRejectsMissingMandatoryParam() {
        javapi.request.Request request = javapi.request.Request.builder()
                .method("POST")
                .path("/bound")
                .body("")
                .build();
        javapi.params.RequestValidationError error = org.junit.jupiter.api.Assertions.assertThrows(
                javapi.params.RequestValidationError.class,
                () -> router.match(HttpMethod.POST, "/bound").route().handler().handle(request));
        org.junit.jupiter.api.Assertions.assertEquals("missing", error.errors().get(0).type());
    }

    @Test
    void dependsParamsResolveFromContainer() {
        DI di = new DI();
        di.component(Greeter.class, EnglishGreeter.class);
        di.component(Counter.class, Counter.class);
        Router diRouter = RouteScanner.scan(
                new Router(), "javapi.ditestroutes", getClass().getClassLoader(), di);
        Request request = Request.builder().method("GET").path("/di/greet").query("name=neo").build();
        Object result = diRouter.match(HttpMethod.GET, "/di/greet").route().handler().handle(request);
        assertTrue(result.toString().contains("Hello neo"), "got: " + result);
        assertTrue(result.toString().contains("counter(Hello x)"), "got: " + result);
    }

    @Test
    void dependsScopedInstanceIsPerRequestAndClosed() {
        DI di = new DI();
        di.component(Greeter.class, EnglishGreeter.class);
        di.component(Counter.class, Counter.class);
        di.requestScoped(RequestScoped.class, ctx -> new RequestScoped());
        Router diRouter = RouteScanner.scan(
                new Router(), "javapi.ditestroutes", getClass().getClassLoader(), di);
        Request request = Request.builder().method("GET").path("/di/scoped").build();
        String first = diRouter.match(HttpMethod.GET, "/di/scoped").route().handler().handle(request).toString();
        String second = diRouter.match(HttpMethod.GET, "/di/scoped").route().handler().handle(request).toString();
        assertNotEquals(first, second);
        assertTrue(RequestScoped.closes >= 2, "request-scoped instances should be closed per request");
    }

    @Test
    void scanRegistersComponentClasses() {
        DI di = new DI();
        di.component(Greeter.class, EnglishGreeter.class);
        di.component(Counter.class, Counter.class);
        RouteScanner.scan(new Router(), "javapi.ditestroutes", getClass().getClassLoader(), di);
        assertNotNull(di.resolveSingleton(EnglishGreeter.class));
    }
}
