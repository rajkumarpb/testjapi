package javapi.params;

final class CoercionError extends RuntimeException {

    private final String type;

    CoercionError(String type, String message) {
        super(message);
        this.type = type;
    }

    String type() {
        return type;
    }
}
