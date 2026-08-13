package javapi.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;
import javapi.di.DI;
import javapi.ditestroutes.Counter;
import javapi.ditestroutes.EnglishGreeter;
import javapi.ditestroutes.Greeter;
import javapi.ditestroutes.RequestScoped;
import javapi.request.Request;

class JavAPITest {

    @Test
    void fluentApiRegistersBindingsAndOverrides() {
        JavAPI app = JavAPI.create();
        app.component(Greeter.class, EnglishGreeter.class)
                .component(Counter.class, Counter.class)
                .override(Greeter.class, name -> "STUB " + name);
        Greeter resolved = (Greeter) app.di().resolveSingleton(Greeter.class);
        assertEquals("STUB neo", resolved.greet("neo"));
    }

    @Test
    void scanRegistersComponentsIntoContainer() {
        JavAPI app = JavAPI.create();
        app.component(Greeter.class, EnglishGreeter.class)
                .component(Counter.class, Counter.class)
                .scan("javapi.ditestroutes");
        assertSame(app.di().resolveSingleton(EnglishGreeter.class),
                app.di().resolveSingleton(EnglishGreeter.class));
    }

    @Test
    void requestScopedFactoryResolvesPerRequest() {
        JavAPI app = JavAPI.create();
        app.requestScoped(RequestScoped.class, ctx -> new RequestScoped());
        DI.Context ctx = app.di().open(Request.builder().method("GET").path("/").build());
        Object a = ctx.resolve(RequestScoped.class);
        assertSame(a, ctx.resolve(RequestScoped.class));
        ctx.close();
    }
}
