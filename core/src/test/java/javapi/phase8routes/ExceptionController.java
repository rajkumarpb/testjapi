package javapi.phase8routes;

import java.util.Map;
import javapi.annotations.ExceptionHandler;
import javapi.annotations.Get;
import javapi.request.Response;

public class ExceptionController {

    @Get("/boom")
    public Map<String, Object> boom() {
        throw new AppException("bad news");
    }

    @ExceptionHandler(AppException.class)
    public Response handle(AppException error) {
        return Response.of(422, Map.of("detail", error.getMessage(), "error", "app_error"));
    }
}
