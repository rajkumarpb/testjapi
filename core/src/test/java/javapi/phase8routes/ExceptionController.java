package javapi.phase8routes;

import java.util.Map;
import javapi.annotations.exception;
import javapi.annotations.get;
import javapi.request.Response;

public class ExceptionController {

    @get("/boom")
    public Map<String, Object> boom() {
        throw new AppException("bad news");
    }

    @exception(AppException.class)
    public Response handle(AppException error) {
        return Response.of(422, Map.of("detail", error.getMessage(), "error", "app_error"));
    }
}
