package javapi.params;

import java.util.List;

public final class RequestValidationError extends RuntimeException {

    private final List<FieldError> errors;

    public RequestValidationError(List<FieldError> errors) {
        super(errors.isEmpty() ? "Validation failed" : errors.get(0).msg());
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }
}
