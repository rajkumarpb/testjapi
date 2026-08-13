package javapi.request;

@FunctionalInterface
public interface ExceptionHandler {
    Response handle(Throwable error);
}
