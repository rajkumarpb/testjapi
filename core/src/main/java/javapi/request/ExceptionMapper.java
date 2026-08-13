package javapi.request;

import java.util.Map;
import javapi.params.RequestValidationError;

public final class ExceptionMapper {

    private ExceptionMapper() {
    }

    public static Response map(Throwable throwable) {
        if (throwable instanceof RequestValidationError e) {
            return Response.of(422, Map.of("detail", e.errors()));
        }
        if (throwable instanceof HttpException e) {
            return Response.of(e.status(), Map.of("detail", e.detail()));
        }
        throwable.printStackTrace(System.err);
        String detail = throwable.getMessage() == null ? "Internal Server Error" : throwable.getMessage();
        return Response.of(500, Map.of("detail", detail));
    }
}
