package javapi.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import javapi.params.FieldError;
import javapi.params.RequestValidationError;

class ExceptionMapperTest {

    private static Map<?, ?> detailOf(Response response) {
        return (Map<?, ?>) response.body();
    }

    @Test
    void validationErrorMapsTo422WithErrors() {
        FieldError error = new FieldError(List.of("query", "limit"), "Input should be a valid integer", "int_parsing");
        Response response = ExceptionMapper.map(new RequestValidationError(List.of(error)));
        assertEquals(422, response.status());
        assertEquals(List.of(error), detailOf(response).get("detail"));
    }

    @Test
    void httpExceptionMapsToItsStatus() {
        Response response = ExceptionMapper.map(new HttpException(404, "No such item"));
        assertEquals(404, response.status());
        assertEquals("No such item", detailOf(response).get("detail"));
    }

    @Test
    void httpExceptionPreservesStructuredDetail() {
        Response response = ExceptionMapper.map(new HttpException(503, Map.of("db", "unavailable")));
        assertEquals(503, response.status());
        assertEquals(Map.of("db", "unavailable"), detailOf(response).get("detail"));
    }

    @Test
    void unknownThrowableMapsTo500WithMessage() {
        Response response = ExceptionMapper.map(new IllegalStateException("kaboom"));
        assertEquals(500, response.status());
        assertEquals("kaboom", detailOf(response).get("detail"));
    }

    @Test
    void unknownThrowableWithoutMessageMapsTo500Generic() {
        Response response = ExceptionMapper.map(new IllegalStateException());
        assertEquals(500, response.status());
        assertEquals("Internal Server Error", detailOf(response).get("detail"));
    }

    @Test
    void uniformErrorShape() {
        for (Throwable t : List.of(
                new RequestValidationError(List.of(new FieldError(List.of("a"), "m", "type"))),
                new HttpException(404, "gone"),
                new RuntimeException("bad"))) {
            Response response = ExceptionMapper.map(t);
            assertTrue(response.body() instanceof Map, "body must be a map");
            assertTrue(((Map<?, ?>) response.body()).containsKey("detail"), "body must contain detail");
        }
    }
}
