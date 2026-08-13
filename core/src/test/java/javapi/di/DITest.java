package javapi.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import javapi.annotations.component;
import javapi.annotations.inject;
import javapi.request.Request;

class DITest {

    interface Greeter {
        String greet(String name);
    }

    @component
    static class EnglishGreeter implements Greeter {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    static class Counter {
        private int value;

        public int value() {
            return value;
        }

        public void increment() {
            value++;
        }
    }

    static class NeedsGreeter {
        private final Greeter greeter;

        @inject
        public NeedsGreeter(Greeter greeter) {
            this.greeter = greeter;
        }

        Greeter greeter() {
            return greeter;
        }
    }

    static class CloseableCounter extends Counter implements AutoCloseable {
        boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    private static Request request() {
        return Request.builder().method("GET").path("/").build();
    }

    @Test
    void registeredInstanceResolvesAsSingleton() {
        DI di = new DI();
        Counter counter = new Counter();
        di.component(Counter.class, counter);
        assertSame(counter, di.resolveSingleton(Counter.class));
    }

    @Test
    void componentAnnotationAutoRegistersSingleton() {
        DI di = new DI();
        assertSame(di.resolveSingleton(EnglishGreeter.class), di.resolveSingleton(EnglishGreeter.class));
    }

    @Test
    void interfaceBindingCreatesImpl() {
        DI di = new DI();
        di.component(Greeter.class, EnglishGreeter.class);
        Greeter greeter = (Greeter) di.resolveSingleton(Greeter.class);
        assertEquals("Hello neo", greeter.greet("neo"));
        assertSame(greeter, di.resolveSingleton(Greeter.class));
    }

    @Test
    void injectConstructorResolvesDependencies() {
        DI di = new DI();
        di.component(Greeter.class, EnglishGreeter.class);
        di.component(NeedsGreeter.class, NeedsGreeter.class);
        assertSame(di.resolveSingleton(Greeter.class),
                ((NeedsGreeter) di.resolveSingleton(NeedsGreeter.class)).greeter());
    }

    @Test
    void overrideReplacesResolution() {
        DI di = new DI();
        di.component(Greeter.class, EnglishGreeter.class);
        Greeter stub = name -> "STUB " + name;
        di.override(Greeter.class, stub);
        assertSame(stub, di.resolveSingleton(Greeter.class));
    }

    @Test
    void requestScopedIsPerContextAndCachedWithinContext() {
        DI di = new DI();
        di.requestScoped(Counter.class, ctx -> new Counter());
        DI.Context ctx1 = di.open(request());
        Object a = ctx1.resolve(Counter.class);
        assertSame(a, ctx1.resolve(Counter.class));
        DI.Context ctx2 = di.open(request());
        assertNotSame(a, ctx2.resolve(Counter.class));
    }

    @Test
    void requestScopedCloseableIsClosedOnContextClose() {
        DI di = new DI();
        di.requestScoped(Counter.class, ctx -> new CloseableCounter());
        DI.Context ctx = di.open(request());
        CloseableCounter counter = (CloseableCounter) ctx.resolve(Counter.class);
        ctx.close();
        assertTrue(counter.closed);
    }

    @Test
    void unresolvedTypeThrows() {
        DI di = new DI();
        assertThrows(DependencyException.class, () -> di.resolveSingleton(Counter.class));
    }

    @Test
    void requestScopedOutsideRequestThrows() {
        DI di = new DI();
        di.requestScoped(Counter.class, ctx -> new Counter());
        assertThrows(DependencyException.class, () -> di.resolveSingleton(Counter.class));
    }
}
