package javapi.params;

import java.util.List;

public record FieldError(List<Object> loc, String msg, String type) {
}
