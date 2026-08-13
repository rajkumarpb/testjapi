package javapi.routing;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import javapi.annotations.Blocking;
import javapi.annotations.Component;
import javapi.annotations.Delete;
import javapi.annotations.EventLoop;
import javapi.annotations.ExceptionHandler;
import javapi.annotations.Get;
import javapi.annotations.HttpMethod;
import javapi.annotations.Middleware;
import javapi.annotations.Patch;
import javapi.annotations.Post;
import javapi.annotations.Put;
import javapi.annotations.Route;
import javapi.annotations.Transaction;
import javapi.config.Config;
import javapi.di.DI;
import javapi.params.ParamBinder;
import javapi.request.Response;

public final class RouteScanner {

    private RouteScanner() {
    }

    public static Router scan(Router router, String packageName, ClassLoader classLoader) {
        return scan(router, packageName, classLoader, new DI());
    }

    public static Router scan(Router router, String packageName, ClassLoader classLoader, DI di) {
        for (Class<?> clazz : PackageScanner.classesInPackage(packageName, classLoader)) {
            scanClass(router, clazz, di);
        }
        return router;
    }

    private static void scanClass(Router router, Class<?> clazz, DI di) {
        if (clazz.isAnnotationPresent(Component.class)) {
            di.registerComponent(clazz);
        }
        if (clazz.isAnnotationPresent(Middleware.class)) {
            registerMiddleware(router, clazz, di);
        }
        Route classRoute = clazz.getAnnotation(Route.class);
        String prefix = classRoute == null ? null : classRoute.value();
        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(ExceptionHandler.class)) {
                registerExceptionMapper(router, clazz, method, di);
                continue;
            }
            Annotation annotation = findRouteAnnotation(method);
            if (annotation == null) {
                continue;
            }
            String path = mergePath(prefix, pathOf(annotation));
            ParamBinder binder = new ParamBinder(method);
            router.register(methodsOf(annotation), path, bind(clazz, method, binder, di),
                    EndpointMeta.of(method, binder.paramInfo()), executionOf(method));
        }
    }

    private static void registerMiddleware(Router router, Class<?> clazz, DI di) {
        Object instance = controllerInstance(clazz, di);
        if (instance instanceof javapi.middleware.Middleware middleware) {
            router.use(middleware);
            return;
        }
        boolean registered = false;
        for (Method method : clazz.getMethods()) {
            if (javapi.middleware.Middleware.class.isAssignableFrom(method.getReturnType())) {
                MethodHandle handle = unreflect(method, instance);
                router.use((javapi.middleware.Middleware) invoke(handle, new Object[0]));
                registered = true;
            }
        }
        if (!registered) {
            throw new IllegalStateException("@Middleware class " + clazz.getName()
                    + " must implement " + javapi.middleware.Middleware.class.getName()
                    + " or expose a public method returning it");
        }
    }

    private static void registerExceptionMapper(Router router, Class<?> clazz, Method method, DI di) {
        ExceptionHandler annotation = method.getAnnotation(ExceptionHandler.class);
        if (method.getParameterTypes().length != 1) {
            throw new IllegalStateException(
                    "@ExceptionHandler method " + clazz.getName() + "." + method.getName()
                            + " must accept exactly one exception parameter");
        }
        Object instance = controllerInstance(clazz, di);
        MethodHandle handle = unreflect(method, instance);
        router.exception(annotation.value(), throwable -> {
            try {
                return (Response) handle.invoke(throwable);
            } catch (Throwable secondary) {
                throw sneakyThrow(secondary);
            }
        });
    }

    private static ExecutionMode executionOf(Method method) {
        boolean inline = method.isAnnotationPresent(EventLoop.class);
        boolean blocking = method.isAnnotationPresent(Blocking.class);
        if (inline && blocking) {
            throw new IllegalStateException(
                    "Method " + method.getDeclaringClass().getName() + "." + method.getName()
                            + " cannot have both @EventLoop and @Blocking");
        }
        if (inline) {
            return ExecutionMode.EVENT_LOOP;
        }
        if (blocking) {
            return ExecutionMode.BLOCKING;
        }
        return ExecutionMode.AUTO;
    }

    private static Handler bind(Class<?> clazz, Method method, ParamBinder binder, DI di) {
        Object instance = controllerInstance(clazz, di);
        MethodHandle handle = unreflect(method, instance);
        List<Integer> depends = binder.dependsPositions();
        if (method.isAnnotationPresent(Transaction.class)) {
            return transactional(handle, binder, depends, di);
        }
        return request -> {
            DI.Context ctx = di.open(request);
            try {
                Object[] args = binder.bind(request);
                for (int position : depends) {
                    Class<?> type = binder.dependsType(position);
                    args[position] = type == Config.class ? Config.load() : ctx.resolve(type);
                }
                return invoke(handle, args);
            } finally {
                ctx.close();
            }
        };
    }

    private static Handler transactional(MethodHandle handle, ParamBinder binder, List<Integer> depends, DI di) {
        return request -> {
            DI.Context ctx = di.open(request);
            Connection connection = null;
            try {
                DataSource dataSource = (DataSource) ctx.resolve(DataSource.class);
                connection = dataSource.getConnection();
                connection.setAutoCommit(false);
                ctx.bind(Connection.class, connection);
                Object[] args = binder.bind(request);
                for (int position : depends) {
                    Class<?> type = binder.dependsType(position);
                    args[position] = type == Config.class ? Config.load() : ctx.resolve(type);
                }
                Object result = invoke(handle, args);
                connection.commit();
                return result;
            } catch (Throwable t) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        t.addSuppressed(rollbackFailure);
                    }
                }
                return sneakyThrow(t);
            } finally {
                ctx.close();
            }
        };
    }

    private static MethodHandle unreflect(Method method, Object instance) {
        try {
            return MethodHandles.publicLookup().unreflect(method).bindTo(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot bind route method " + method, e);
        }
    }

    private static Object invoke(MethodHandle handle, Object[] args) {
        try {
            return handle.invokeWithArguments(args);
        } catch (Throwable t) {
            return sneakyThrow(t);
        }
    }

    private static Object controllerInstance(Class<?> clazz, DI di) {
        if (clazz.isAnnotationPresent(Component.class) || di.isRegistered(clazz)) {
            return di.resolveSingleton(clazz);
        }
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            return di.instantiate(clazz);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Route class must be public with a no-arg constructor: " + clazz.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }

    private static Annotation findRouteAnnotation(Method method) {
        if (method.isAnnotationPresent(Get.class)) {
            return method.getAnnotation(Get.class);
        }
        if (method.isAnnotationPresent(Post.class)) {
            return method.getAnnotation(Post.class);
        }
        if (method.isAnnotationPresent(Put.class)) {
            return method.getAnnotation(Put.class);
        }
        if (method.isAnnotationPresent(Delete.class)) {
            return method.getAnnotation(Delete.class);
        }
        if (method.isAnnotationPresent(Patch.class)) {
            return method.getAnnotation(Patch.class);
        }
        if (method.isAnnotationPresent(Route.class)) {
            return method.getAnnotation(Route.class);
        }
        return null;
    }

    private static String pathOf(Annotation annotation) {
        return switch (annotation) {
            case Get a -> a.value();
            case Post a -> a.value();
            case Put a -> a.value();
            case Delete a -> a.value();
            case Patch a -> a.value();
            case Route a -> a.value();
            default -> throw new IllegalStateException("Unexpected annotation " + annotation);
        };
    }

    private static Set<HttpMethod> methodsOf(Annotation annotation) {
        return switch (annotation) {
            case Get ignored -> Set.of(HttpMethod.GET);
            case Post ignored -> Set.of(HttpMethod.POST);
            case Put ignored -> Set.of(HttpMethod.PUT);
            case Delete ignored -> Set.of(HttpMethod.DELETE);
            case Patch ignored -> Set.of(HttpMethod.PATCH);
            case Route r -> r.methods().length == 0
                    ? EnumSet.allOf(HttpMethod.class)
                    : Set.of(r.methods());
            default -> throw new IllegalStateException("Unexpected annotation " + annotation);
        };
    }

    private static String mergePath(String prefix, String methodPath) {
        String mp = methodPath == null || methodPath.isEmpty() ? "" : methodPath;
        if (!mp.isEmpty() && !mp.startsWith("/")) {
            mp = "/" + mp;
        }
        if (prefix == null || prefix.isEmpty()) {
            return mp.isEmpty() ? "/" : mp;
        }
        String p = stripTrailingSlash(prefix);
        if (mp.isEmpty()) {
            return p;
        }
        if ("/".equals(p)) {
            return mp;
        }
        return p + mp;
    }

    private static String stripTrailingSlash(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
