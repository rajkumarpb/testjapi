package javapi.schema;

import java.lang.reflect.Type;
import java.util.List;

public final class Schema {

    public enum Kind {
        STRING, INT, LONG, DOUBLE, FLOAT, SHORT, BYTE, BOOLEAN, CHAR,
        ENUM, UUID, LOCAL_DATE, LOCAL_DATE_TIME, OPTIONAL, LIST, SET, MAP, RECORD, OBJECT
    }

    public record Field(String name, Type type, Class<?> rawClass, Schema schema, Constraints constraints) {
    }

    private final Kind kind;
    private final Class<?> raw;
    private final Schema inner;
    private final Schema value;
    private final List<Field> fields;

    private Schema(Kind kind, Class<?> raw, Schema inner, Schema value, List<Field> fields) {
        this.kind = kind;
        this.raw = raw;
        this.inner = inner;
        this.value = value;
        this.fields = fields == null ? List.of() : List.copyOf(fields);
    }

    static Schema of(Kind kind, Class<?> raw) {
        return new Schema(kind, raw, null, null, null);
    }

    static Schema single(Kind kind, Class<?> raw, Schema inner) {
        return new Schema(kind, raw, inner, null, null);
    }

    static Schema map(Class<?> raw, Schema value) {
        return new Schema(Kind.MAP, raw, null, value, null);
    }

    static Schema record(Class<?> raw, List<Field> fields) {
        return new Schema(Kind.RECORD, raw, null, null, fields);
    }

    public Kind kind() {
        return kind;
    }

    public Class<?> raw() {
        return raw;
    }

    public Schema inner() {
        return inner;
    }

    public Schema value() {
        return value;
    }

    public List<Field> fields() {
        return fields;
    }

    public boolean isOptional() {
        return kind == Kind.OPTIONAL;
    }
}
