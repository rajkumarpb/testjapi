package javapi.openapi;

import java.util.Map;
import javapi.request.Response;
import javapi.routing.Handler;
import javapi.routing.Router;

public final class OpenApi {

    private static final String CONTENT_TYPE_HTML = "text/html; charset=utf-8";

    private OpenApi() {
    }

    public static Handler specHandler(Router router) {
        CachedSpec cached = new CachedSpec(router);
        return request -> cached.spec();
    }

    public static Handler docsHandler() {
        return request -> Response.of(200, swaggerUiHtml()).withHeader("content-type", CONTENT_TYPE_HTML);
    }

    public static Handler redocHandler() {
        return request -> Response.of(200, redocHtml()).withHeader("content-type", CONTENT_TYPE_HTML);
    }

    private static final class CachedSpec {

        private final Router router;
        private volatile Map<String, Object> cached;
        private volatile long cachedVersion = -1;

        CachedSpec(Router router) {
            this.router = router;
        }

        Map<String, Object> spec() {
            long version = router.version();
            Map<String, Object> current = cached;
            if (current == null || cachedVersion != version) {
                current = OpenApiGenerator.generate(router);
                cached = current;
                cachedVersion = version;
            }
            return current;
        }
    }

    private static String swaggerUiHtml() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8"/>
                <title>javapi - Swagger UI</title>
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.18.2/swagger-ui.css"/>
                </head>
                <body>
                <div id="swagger-ui"></div>
                <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.18.2/swagger-ui-bundle.js"></script>
                <script>
                window.onload = () => {
                  window.ui = SwaggerUIBundle({
                    url: "/openapi.json",
                    dom_id: "#swagger-ui",
                    deepLinking: true,
                    presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset],
                    layout: "BaseLayout"
                  });
                };
                </script>
                </body>
                </html>
                """;
    }

    private static String redocHtml() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8"/>
                <title>javapi - ReDoc</title>
                </head>
                <body>
                <div id="redoc"></div>
                <script src="https://cdn.jsdelivr.net/npm/redoc@2/bundles/redoc.standalone.js"></script>
                <script>
                Redoc.init("/openapi.json", {}, document.getElementById("redoc"));
                </script>
                </body>
                </html>
                """;
    }
}
