package javapi.request;

public class HttpException extends RuntimeException {

    private final int status;
    private final Object detail;

    public HttpException(int status, Object detail) {
        super(String.valueOf(detail));
        this.status = status;
        this.detail = detail;
    }

    public HttpException(int status, String detail) {
        this(status, (Object) detail);
    }

    public int status() {
        return status;
    }

    public Object detail() {
        return detail;
    }
}
