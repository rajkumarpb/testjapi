package javapi.phase8routes;

import java.util.Map;
import javapi.annotations.Middleware;
import javapi.middleware.Next;
import javapi.request.Request;
import javapi.request.Response;

@Middleware
public class HeaderMiddleware implements javapi.middleware.Middleware {

    @Override
    public Object handle(Request request, Next next) {
        Object result = next.next(request);
        Response response = result instanceof Response r ? r : Response.ok(result);
        return response.withHeader("X-Powered-By", "javapi");
    }
}
