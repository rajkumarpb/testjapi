package javapi.params;

final class BindingError extends RuntimeException {

    private final FieldError error;

    BindingError(FieldError error) {
        super(error.msg());
        this.error = error;
    }

    FieldError error() {
        return error;
    }
}
