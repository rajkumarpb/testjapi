package javapi.phase8routes;

import java.util.Map;
import javapi.annotations.middleware;
import javapi.middleware.Middleware;
import javapi.middleware.Next;
import javapi.request.Request;
import javapi.request.Response;

@middleware
public class HeaderMiddleware implements Middleware {

    @Override
    public Object handle(Request request, Next next) {
        Object result = next.next(request);
        Response response = result instanceof Response r ? r : Response.ok(result);
        return response.withHeader("X-Powered-By", "javapi");
    }
}
