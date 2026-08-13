package javapi.di;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javapi.annotations.component;
import javapi.annotations.inject;
import javapi.request.Request;

public final class DI {

    public enum Scope {
        SINGLETON, REQUEST
    }

    @FunctionalInterface
    public interface Factory<T> {
        T create(Context context) throws Exception;
    }

    private record Binding(Scope scope, Object instance, Factory<?> factory) {
    }

    private final Map<Class<?>, Binding> bindings = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> overrides = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();

    public <T> DI component(Class<T> type, T instance) {
        bindings.put(type, new Binding(Scope.SINGLETON, instance, null));
        return this;
    }

    public <T> DI component(Class<T> type, Class<? extends T> impl) {
        bindings.put(type, new Binding(Scope.SINGLETON, null, ctx -> instantiate(impl, this)));
        return this;
    }

    public <T> DI factory(Class<T> type, Factory<T> factory) {
        bindings.put(type, new Binding(Scope.SINGLETON, null, factory));
        return this;
    }

    public <T> DI requestScoped(Class<T> type, Factory<T> factory) {
        bindings.put(type, new Binding(Scope.REQUEST, null, factory));
        return this;
    }

    public <T> DI override(Class<T> type, T instance) {
        overrides.put(type, instance);
        return this;
    }

    public void registerComponent(Class<?> type) {
        bindings.computeIfAbsent(type, t -> new Binding(Scope.SINGLETON, null, ctx -> instantiate(t, this)));
    }

    public boolean isRegistered(Class<?> type) {
        return bindings.containsKey(type) || overrides.containsKey(type);
    }

    public Set<Class<?>> registeredTypes() {
        return Set.copyOf(bindings.keySet());
    }

    public Object resolveSingleton(Class<?> type) {
        Object override = overrides.get(type);
        if (override != null) {
            return override;
        }
        Binding binding = lookup(type);
        if (binding == null) {
            throw unresolved(type);
        }
        if (binding.instance() != null) {
            return binding.instance();
        }
        if (binding.scope() == Scope.REQUEST) {
            throw new DependencyException("Type " + type.getName()
                    + " is request-scoped and cannot be resolved outside a request");
        }
        return singletons.computeIfAbsent(type, t -> create(binding, null));
    }

    public Object instantiate(Class<?> type) {
        return instantiate(type, this);
    }

    public Context open(Request request) {
        return new Context(this, request);
    }

    private Binding lookup(Class<?> type) {
        Binding binding = bindings.get(type);
        if (binding != null) {
            return binding;
        }
        if (type.isAnnotationPresent(component.class)) {
            registerComponent(type);
            return bindings.get(type);
        }
        return null;
    }

    private Object create(Binding binding, Context context) {
        try {
            return binding.factory().create(context);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyException("Failed to create dependency", e);
        }
    }

    private static Object instantiate(Class<?> type, DI di) {
        try {
            Constructor<?> chosen = null;
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                if (ctor.isAnnotationPresent(inject.class)) {
                    chosen = ctor;
                    break;
                }
            }
            Constructor<?> ctor = chosen != null ? chosen : type.getDeclaredConstructor();
            ctor.trySetAccessible();
            Class<?>[] parameterTypes = ctor.getParameterTypes();
            Object[] args = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                args[i] = di.resolveSingleton(parameterTypes[i]);
            }
            return ctor.newInstance(args);
        } catch (DependencyException e) {
            throw e;
        } catch (ReflectiveOperationException e) {
            throw new DependencyException("Cannot instantiate component " + type.getName()
                    + " (needs a public no-arg constructor or an @inject constructor)", e);
        }
    }

    private static DependencyException unresolved(Class<?> type) {
        return new DependencyException("No binding for " + type.getName()
                + " — register it with .component(...) or annotate it @component");
    }

    public static final class Context {

        private final DI di;
        private final Request request;
        private final Map<Class<?>, Object> requestScoped = new HashMap<>();

        private Context(DI di, Request request) {
            this.di = di;
            this.request = request;
        }

        public Request request() {
            return request;
        }

        public Object resolve(Class<?> type) {
            Object override = di.overrides.get(type);
            if (override != null) {
                return override;
            }
            Binding binding = di.lookup(type);
            if (binding == null) {
                throw unresolved(type);
            }
            if (binding.instance() != null) {
                return binding.instance();
            }
            if (binding.scope() == Scope.SINGLETON) {
                return di.singletons.computeIfAbsent(type, t -> di.create(binding, this));
            }
            Object existing = requestScoped.get(type);
            if (existing != null) {
                return existing;
            }
            Object created = di.create(binding, this);
            requestScoped.put(type, created);
            return created;
        }

        public void bind(Class<?> type, Object instance) {
            requestScoped.put(type, instance);
        }

        public void close() {
            for (Object value : requestScoped.values()) {
                if (value instanceof AutoCloseable closeable) {
                    try {
                        closeable.close();
                    } catch (Exception ignored) {
                    }
                }
            }
            requestScoped.clear();
        }
    }
}
