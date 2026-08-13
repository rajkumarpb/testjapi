package javapi.core;

import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.Set;
import javapi.annotations.HttpMethod;
import javapi.config.Config;
import javapi.di.DI;
import javapi.middleware.Cors;
import javapi.middleware.Middleware;
import javapi.request.ExceptionHandler;
import javapi.routing.Handler;
import javapi.routing.RouteScanner;
import javapi.routing.Router;
import javapi.staticfiles.StaticFiles;
import javapi.websocket.WebSocketEndpoint;

public final class JavAPI {

    private final Router router = new Router();
    private final DI di = new DI();
    private ServerSettings settings = ServerSettings.create();
    private boolean hostExplicit;
    private boolean portExplicit;
    private boolean workersExplicit;
    private boolean inlineExplicit;
    private boolean logExplicit;

    private JavAPI() {
    }

    public static JavAPI create() {
        return new JavAPI();
    }

    public DI di() {
        return di;
    }

    public <T> JavAPI component(Class<T> type, T instance) {
        di.component(type, instance);
        return this;
    }

    public <T> JavAPI component(Class<T> type, Class<? extends T> impl) {
        di.component(type, impl);
        return this;
    }

    public <T> JavAPI requestScoped(Class<T> type, DI.Factory<T> factory) {
        di.requestScoped(type, factory);
        return this;
    }

    public <T> JavAPI override(Class<T> type, T instance) {
        di.override(type, instance);
        return this;
    }

    public JavAPI jdbc(String url) {
        return jdbc(url, null, null);
    }

    public JavAPI jdbc(String url, String user, String password) {
        javapi.jdbc.JdbcSetup.register(di, url, user, password);
        return this;
    }

    public JavAPI get(String path, Handler handler) {
        router.register(HttpMethod.GET, path, handler);
        return this;
    }

    public JavAPI post(String path, Handler handler) {
        router.register(HttpMethod.POST, path, handler);
        return this;
    }

    public JavAPI put(String path, Handler handler) {
        router.register(HttpMethod.PUT, path, handler);
        return this;
    }

    public JavAPI delete(String path, Handler handler) {
        router.register(HttpMethod.DELETE, path, handler);
        return this;
    }

    public JavAPI patch(String path, Handler handler) {
        router.register(HttpMethod.PATCH, path, handler);
        return this;
    }

    public JavAPI route(String path, Handler handler, HttpMethod... methods) {
        router.register(Set.of(methods), path, handler);
        return this;
    }

    public JavAPI scan(String packageName) {
        RouteScanner.scan(router, packageName, Thread.currentThread().getContextClassLoader(), di);
        return this;
    }

    public JavAPI use(Middleware middleware) {
        router.use(middleware);
        return this;
    }

    public JavAPI cors(Cors cors) {
        router.use(cors);
        return this;
    }

    public JavAPI staticFiles(String prefix, Path directory) {
        router.use(StaticFiles.fromDirectory(prefix, directory));
        return this;
    }

    public JavAPI staticFiles(String prefix) {
        router.use(StaticFiles.fromClasspath(prefix, Thread.currentThread().getContextClassLoader()));
        return this;
    }

    public JavAPI ws(String path, WebSocketEndpoint endpoint) {
        router.registerWs(path, endpoint);
        return this;
    }

    public JavAPI exception(Class<? extends Throwable> type, ExceptionHandler handler) {
        router.exception(type, handler);
        return this;
    }

    public JavAPI host(String host) {
        settings = settings.withHost(host);
        hostExplicit = true;
        return this;
    }

    public JavAPI port(int port) {
        settings = settings.withPort(port);
        portExplicit = true;
        return this;
    }

    public JavAPI workers(int workers) {
        settings = settings.withWorkers(workers);
        workersExplicit = true;
        return this;
    }

    public JavAPI eventLoopInline(boolean eventLoopInline) {
        settings = settings.withEventLoopInline(eventLoopInline);
        inlineExplicit = true;
        return this;
    }

    public JavAPI logRequests(boolean logRequests) {
        settings = settings.withLogRequests(logRequests);
        logExplicit = true;
        return this;
    }

    public Router router() {
        return router;
    }

    public Server start() {
        for (DocsProvider provider : ServiceLoader.load(DocsProvider.class)) {
            provider.install(router);
        }
        Config config = Config.load();
        if (!di.isRegistered(javax.sql.DataSource.class)) {
            String url = config.get("db.url");
            if (url != null) {
                javapi.jdbc.JdbcSetup.register(di, url, config.get("db.user"), config.get("db.password"));
            }
        }
        ServerSettings resolved = new ServerSettings(
                hostExplicit ? settings.host() : config.get("server.host", settings.host()),
                portExplicit ? settings.port() : config.getInt("server.port", settings.port()),
                workersExplicit ? settings.workers() : config.getInt("server.workers", settings.workers()),
                inlineExplicit ? settings.eventLoopInline()
                        : config.getBoolean("server.eventLoopInline", settings.eventLoopInline()),
                logExplicit ? settings.logRequests()
                        : config.getBoolean("server.logRequests", settings.logRequests()));
        ServerFactory factory = ServiceLoader.load(ServerFactory.class).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No ServerFactory implementation found on the classpath"));
        return factory.create(router, resolved).start();
    }
}
