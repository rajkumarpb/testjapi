package javapi.params;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.UUID;

public final class ScalarCoercer {

    private ScalarCoercer() {
    }

    public static Object coerce(String value, Type type) {
        if (type instanceof ParameterizedType parameterized
                && parameterized.getRawType() == Optional.class) {
            Object inner = coerce(value, parameterized.getActualTypeArguments()[0]);
            return Optional.ofNullable(inner);
        }
        return coerceScalar(value, (Class<?>) type);
    }

    private static Object coerceScalar(String value, Class<?> raw) {
        if (raw == String.class) {
            return value;
        }
        if (raw == int.class || raw == Integer.class) {
            return parse(value, Integer::parseInt, "int_parsing", "value is not a valid integer");
        }
        if (raw == long.class || raw == Long.class) {
            return parse(value, Long::parseLong, "int_parsing", "value is not a valid integer");
        }
        if (raw == short.class || raw == Short.class) {
            return parse(value, Short::parseShort, "int_parsing", "value is not a valid integer");
        }
        if (raw == byte.class || raw == Byte.class) {
            return parse(value, Byte::parseByte, "int_parsing", "value is not a valid integer");
        }
        if (raw == double.class || raw == Double.class) {
            return parse(value, Double::parseDouble, "float_parsing", "value is not a valid float");
        }
        if (raw == float.class || raw == Float.class) {
            return parse(value, Float::parseFloat, "float_parsing", "value is not a valid float");
        }
        if (raw == boolean.class || raw == Boolean.class) {
            if ("true".equalsIgnoreCase(value)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(value)) {
                return Boolean.FALSE;
            }
            throw new CoercionError("bool_parsing", "value is not a valid boolean");
        }
        if (raw == char.class || raw == Character.class) {
            return value.isEmpty() ? '\0' : value.charAt(0);
        }
        if (raw.isEnum()) {
            return enumValue(raw, value);
        }
        if (raw == UUID.class) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException e) {
                throw new CoercionError("uuid_parsing", "value is not a valid UUID");
            }
        }
        throw new CoercionError("unsupported", "Unsupported parameter type " + raw.getName());
    }

    private interface ThrowingParser<T> {
        T parse(String value);
    }

    private static Object parse(String value, ThrowingParser<?> parser, String type, String message) {
        try {
            return parser.parse(value);
        } catch (RuntimeException e) {
            throw new CoercionError(type, message);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> raw, String value) {
        for (Enum constant : (Enum[]) raw.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        throw new CoercionError("enum", "value is not a valid " + raw.getSimpleName());
    }
}
