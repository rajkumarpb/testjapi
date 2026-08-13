package javapi.schema;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public final class Schemas {

    private static final ConcurrentHashMap<Type, Schema> CACHE = new ConcurrentHashMap<>();

    private Schemas() {
    }

    public static Schema schemaOf(Type type) {
        return CACHE.computeIfAbsent(type, Schemas::build);
    }

    private static Schema build(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Type[] args = parameterized.getActualTypeArguments();
            if (raw == Optional.class) {
                return Schema.single(Schema.Kind.OPTIONAL, raw, build(args[0]));
            }
            if (raw == List.class) {
                return Schema.single(Schema.Kind.LIST, raw, build(args[0]));
            }
            if (raw == Set.class) {
                return Schema.single(Schema.Kind.SET, raw, build(args[0]));
            }
            if (raw == Map.class) {
                return Schema.map(raw, build(args[1]));
            }
            if (raw == CompletableFuture.class || raw == CompletionStage.class || raw == Future.class) {
                return build(args[0]);
            }
        }
        Class<?> c = (Class<?>) type;
        if (c == String.class) {
            return Schema.of(Schema.Kind.STRING, c);
        }
        if (c == int.class || c == Integer.class) {
            return Schema.of(Schema.Kind.INT, c);
        }
        if (c == long.class || c == Long.class) {
            return Schema.of(Schema.Kind.LONG, c);
        }
        if (c == double.class || c == Double.class) {
            return Schema.of(Schema.Kind.DOUBLE, c);
        }
        if (c == float.class || c == Float.class) {
            return Schema.of(Schema.Kind.FLOAT, c);
        }
        if (c == short.class || c == Short.class) {
            return Schema.of(Schema.Kind.SHORT, c);
        }
        if (c == byte.class || c == Byte.class) {
            return Schema.of(Schema.Kind.BYTE, c);
        }
        if (c == boolean.class || c == Boolean.class) {
            return Schema.of(Schema.Kind.BOOLEAN, c);
        }
        if (c == char.class || c == Character.class) {
            return Schema.of(Schema.Kind.CHAR, c);
        }
        if (c == UUID.class) {
            return Schema.of(Schema.Kind.UUID, c);
        }
        if (c == LocalDate.class) {
            return Schema.of(Schema.Kind.LOCAL_DATE, c);
        }
        if (c == LocalDateTime.class) {
            return Schema.of(Schema.Kind.LOCAL_DATE_TIME, c);
        }
        if (c == Object.class) {
            return Schema.of(Schema.Kind.OBJECT, c);
        }
        if (c.isEnum()) {
            return Schema.of(Schema.Kind.ENUM, c);
        }
        if (c.isRecord()) {
            return buildRecord(c);
        }
        return Schema.of(Schema.Kind.OBJECT, c);
    }

    private static Schema buildRecord(Class<?> c) {
        List<Schema.Field> fields = new ArrayList<>();
        for (RecordComponent component : c.getRecordComponents()) {
            fields.add(new Schema.Field(
                    component.getName(),
                    component.getGenericType(),
                    component.getType(),
                    build(component.getGenericType()),
                    Constraints.of(component)));
        }
        return Schema.record(c, fields);
    }
}
