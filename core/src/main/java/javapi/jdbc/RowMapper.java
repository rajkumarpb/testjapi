package javapi.jdbc;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface RowMapper<T> {

    T map(ResultSet row) throws SQLException;

    static <T> RowMapper<T> from(Class<T> recordClass) {
        if (!recordClass.isRecord()) {
            throw new IllegalArgumentException("RowMapper.from requires a record type, got " + recordClass.getName());
        }
        return row -> instantiate(recordClass, row);
    }

    private static <T> T instantiate(Class<T> recordClass, ResultSet row) throws SQLException {
        RecordComponent[] components = recordClass.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            types[i] = component.getType();
            values[i] = value(row, component.getName(), component.getGenericType());
        }
        try {
            Constructor<T> constructor = recordClass.getDeclaredConstructor(types);
            constructor.trySetAccessible();
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot construct record " + recordClass.getName(), e);
        }
    }

    private static Object value(ResultSet row, String name, Type genericType) throws SQLException {
        int index = columnIndex(row, name);
        Class<?> raw = rawType(genericType);
        if (raw == Optional.class) {
            Type inner = genericType instanceof ParameterizedType p
                    ? p.getActualTypeArguments()[0]
                    : Object.class;
            if (index < 0) {
                return Optional.empty();
            }
            Object value = row.getObject(index);
            return value == null ? Optional.empty() : Optional.ofNullable(convert(row, index, value, rawType(inner)));
        }
        if (index < 0) {
            return absent(raw);
        }
        Object value = row.getObject(index);
        if (value == null) {
            return absent(raw);
        }
        return convert(row, index, value, raw);
    }

    private static int columnIndex(ResultSet row, String name) throws SQLException {
        ResultSetMetaData meta = row.getMetaData();
        int index = find(meta, name);
        if (index < 0) {
            String snake = toSnakeCase(name);
            if (!snake.equals(name)) {
                index = find(meta, snake);
            }
        }
        return index;
    }

    private static int find(ResultSetMetaData meta, String name) throws SQLException {
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (meta.getColumnLabel(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (meta.getColumnName(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String toSnakeCase(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (sb.length() > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        return (Class<?>) type;
    }

    private static Object absent(Class<?> raw) {
        if (raw == int.class || raw == long.class || raw == short.class || raw == byte.class) {
            return 0;
        }
        if (raw == double.class || raw == float.class) {
            return 0.0;
        }
        if (raw == boolean.class) {
            return Boolean.FALSE;
        }
        if (raw == char.class) {
            return '\0';
        }
        return null;
    }

    private static Object convert(ResultSet row, int index, Object raw, Class<?> target) throws SQLException {
        if (target.isInstance(raw)) {
            return raw;
        }
        if (target == String.class) {
            return row.getString(index);
        }
        if (target == int.class || target == Integer.class) {
            return row.getInt(index);
        }
        if (target == long.class || target == Long.class) {
            return row.getLong(index);
        }
        if (target == double.class || target == Double.class) {
            return row.getDouble(index);
        }
        if (target == float.class || target == Float.class) {
            return row.getFloat(index);
        }
        if (target == short.class || target == Short.class) {
            return row.getShort(index);
        }
        if (target == byte.class || target == Byte.class) {
            return row.getByte(index);
        }
        if (target == boolean.class || target == Boolean.class) {
            return row.getBoolean(index);
        }
        if (target == char.class || target == Character.class) {
            String string = row.getString(index);
            return string == null || string.isEmpty() ? '\0' : string.charAt(0);
        }
        if (target == UUID.class) {
            Object object = row.getObject(index);
            if (object instanceof UUID uuid) {
                return uuid;
            }
            String string = object == null ? null : object.toString();
            return string == null ? null : UUID.fromString(string);
        }
        if (target == LocalDate.class) {
            return toLocalDate(raw);
        }
        if (target == LocalDateTime.class) {
            return toLocalDateTime(raw);
        }
        if (target.isEnum()) {
            String string = row.getString(index);
            return enumValue(target, string);
        }
        if (target.isArray() && raw != null && raw.getClass().isArray()) {
            return toArray(target, raw);
        }
        return raw;
    }

    private static LocalDate toLocalDate(Object raw) {
        if (raw instanceof LocalDate date) {
            return date;
        }
        if (raw instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (raw instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (raw instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (raw instanceof String string) {
            return LocalDate.parse(string);
        }
        return null;
    }

    private static LocalDateTime toLocalDateTime(Object raw) {
        if (raw instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (raw instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (raw instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay();
        }
        if (raw instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        if (raw instanceof String string) {
            return LocalDateTime.parse(string);
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> target, String value) {
        for (Enum constant : (Enum[]) target.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown " + target.getSimpleName() + " value '" + value + "'");
    }

    private static Object toArray(Class<?> target, Object raw) {
        Object array = Array.newInstance(target.getComponentType(), Array.getLength(raw));
        for (int i = 0; i < Array.getLength(raw); i++) {
            Array.set(array, i, Array.get(raw, i));
        }
        return array;
    }
}
