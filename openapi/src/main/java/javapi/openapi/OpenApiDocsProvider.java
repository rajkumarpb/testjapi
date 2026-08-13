package javapi.openapi;

import java.util.Set;
import javapi.annotations.HttpMethod;
import javapi.core.DocsProvider;
import javapi.routing.Router;

public final class OpenApiDocsProvider implements DocsProvider {

    @Override
    public void install(Router router) {
        router.register(Set.of(HttpMethod.GET), "/openapi.json", OpenApi.specHandler(router));
        router.register(Set.of(HttpMethod.GET), "/docs", OpenApi.docsHandler());
        router.register(Set.of(HttpMethod.GET), "/redoc", OpenApi.redocHandler());
    }
}
