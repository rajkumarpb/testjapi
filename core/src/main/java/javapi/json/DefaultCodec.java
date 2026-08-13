package javapi.json;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class DefaultCodec implements Codec {

    private static final ThreadLocal<StringBuilder> BUFFER = ThreadLocal.withInitial(StringBuilder::new);

    @Override
    public String write(Object value) {
        StringBuilder out = BUFFER.get();
        out.setLength(0);
        writeValue(new Json.StringBuilderJsonOutput(out), value);
        return out.toString();
    }

    @Override
    public void writeTo(Object value, JsonOutput out) {
        writeValue(out, value);
    }

    @Override
    public Object read(String json, Type targetType) {
        Object value = new JsonParser(json).parse();
        return convert(value, targetType);
    }

    private void writeValue(JsonOutput out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            out.append('"');
            Json.appendEscaped(out, s);
            out.append('"');
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(String.valueOf(value));
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append('"');
                Json.appendEscaped(out, String.valueOf(entry.getKey()));
                out.append("\":");
                writeValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(out, item);
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                writeValue(out, Array.get(value, i));
            }
            out.append(']');
        } else if (value instanceof Record record) {
            out.append('{');
            boolean first = true;
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append('"');
                out.append(component.getName());
                out.append("\":");
                try {
                    java.lang.reflect.Method accessor = component.getAccessor();
                    accessor.trySetAccessible();
                    writeValue(out, accessor.invoke(record));
                } catch (ReflectiveOperationException e) {
                    out.append("null");
                }
            }
            out.append('}');
        } else if (value instanceof Optional<?> optional) {
            writeValue(out, optional.orElse(null));
        } else {
            out.append('"');
            Json.appendEscaped(out, String.valueOf(value));
            out.append('"');
        }
    }

    private Object convert(Object value, Type type) {
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            if (raw == Optional.class) {
                Type inner = parameterized.getActualTypeArguments()[0];
                if (value == null) {
                    return Optional.empty();
                }
                return Optional.ofNullable(convert(value, inner));
            }
            if (raw == List.class) {
                return convertList(value, parameterized.getActualTypeArguments()[0]);
            }
            if (raw == Set.class) {
                return Set.copyOf((List<?>) convertList(value, parameterized.getActualTypeArguments()[0]));
            }
            if (raw == Map.class) {
                return convertMap(value, parameterized.getActualTypeArguments()[1]);
            }
            return convertToClass(value, raw);
        }
        if (value == null) {
            return null;
        }
        return convertToClass(value, (Class<?>) type);
    }

    private List<Object> convertList(Object value, Type elementType) {
        List<?> items = (List<?>) value;
        List<Object> out = new ArrayList<>(items.size());
        for (Object item : items) {
            out.add(convert(item, elementType));
        }
        return out;
    }

    private Map<String, Object> convertMap(Object value, Type valueType) {
        Map<?, ?> source = (Map<?, ?>) value;
        Map<String, Object> out = new LinkedHashMap<>(source.size());
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            out.put(String.valueOf(entry.getKey()), convert(entry.getValue(), valueType));
        }
        return out;
    }

    private Object convertToClass(Object value, Class<?> raw) {
        if (raw == String.class) {
            return value instanceof String s ? s : String.valueOf(value);
        }
        if (raw == int.class || raw == Integer.class) {
            return requireNumber(value, raw).intValue();
        }
        if (raw == long.class || raw == Long.class) {
            return requireNumber(value, raw).longValue();
        }
        if (raw == double.class || raw == Double.class) {
            return requireNumber(value, raw).doubleValue();
        }
        if (raw == float.class || raw == Float.class) {
            return requireNumber(value, raw).floatValue();
        }
        if (raw == short.class || raw == Short.class) {
            return requireNumber(value, raw).shortValue();
        }
        if (raw == byte.class || raw == Byte.class) {
            return requireNumber(value, raw).byteValue();
        }
        if (raw == boolean.class || raw == Boolean.class) {
            if (value instanceof Boolean b) {
                return b;
            }
            throw new JsonException("Expected a boolean for " + raw.getName() + " but got " + typeName(value));
        }
        if (raw == char.class || raw == Character.class) {
            String s = String.valueOf(value);
            return s.isEmpty() ? '\0' : s.charAt(0);
        }
        if (raw == Object.class) {
            return value;
        }
        if (raw.isEnum()) {
            return enumValue(raw, String.valueOf(value));
        }
        if (raw.isRecord()) {
            return convertRecord(raw, value);
        }
        return value;
    }

    private Number requireNumber(Object value, Class<?> raw) {
        if (value instanceof Number n) {
            return n;
        }
        throw new JsonException("Expected a number for " + raw.getName() + " but got " + typeName(value));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object enumValue(Class<?> raw, String name) {
        for (Enum constant : (Enum[]) raw.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name)) {
                return constant;
            }
        }
        throw new JsonException("Value '" + name + "' is not a valid " + raw.getSimpleName());
    }

    private Object convertRecord(Class<?> raw, Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new JsonException("Expected a JSON object for " + raw.getName() + " but got " + typeName(value));
        }
        RecordComponent[] components = raw.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            types[i] = component.getType();
            Object fieldValue = map.get(component.getName());
            args[i] = fieldValue == null
                    ? defaultValue(component)
                    : convert(fieldValue, component.getGenericType());
        }
        try {
            Constructor<?> constructor = raw.getDeclaredConstructor(types);
            constructor.trySetAccessible();
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new JsonException("Failed to construct " + raw.getName(), e);
        }
    }

    private Object defaultValue(RecordComponent component) {
        Class<?> type = component.getType();
        if (type == Optional.class) {
            return Optional.empty();
        }
        if (type == String.class) {
            return "";
        }
        if (type == int.class || type == long.class || type == short.class || type == byte.class) {
            return 0;
        }
        if (type == double.class || type == float.class) {
            return 0.0;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == List.class || type == Set.class) {
            return type == List.class ? List.of() : Set.of();
        }
        return null;
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static final class JsonParser {

        private final String text;
        private int pos;

        JsonParser(String text) {
            this.text = text == null ? "" : text;
        }

        Object parse() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (pos < text.length()) {
                throw new JsonException("Trailing content at position " + pos);
            }
            return value;
        }

        private Object parseValue() {
            if (pos >= text.length()) {
                throw new JsonException("Unexpected end of input");
            }
            return switch (text.charAt(pos)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> {
                    expect("true");
                    yield Boolean.TRUE;
                }
                case 'f' -> {
                    expect("false");
                    yield Boolean.FALSE;
                }
                case 'n' -> {
                    expect("null");
                    yield null;
                }
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            pos++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return map;
                } else {
                    throw new JsonException("Expected ',' or '}' at position " + pos);
                }
            }
        }

        private List<Object> parseArray() {
            pos++;
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return list;
                } else {
                    throw new JsonException("Expected ',' or ']' at position " + pos);
                }
            }
        }

        private String parseString() {
            if (peek() != '"') {
                throw new JsonException("Expected '\"' at position " + pos);
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw new JsonException("Unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= text.length()) {
                        throw new JsonException("Unterminated escape");
                    }
                    char esc = text.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw new JsonException("Invalid \\u escape");
                            }
                            sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw new JsonException("Invalid escape '\\" + esc + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Object parseNumber() {
            int start = pos;
            while (pos < text.length() && isNumberChar(text.charAt(pos))) {
                pos++;
            }
            String token = text.substring(start, pos);
            if (token.isEmpty()) {
                throw new JsonException("Unexpected character '" + text.charAt(pos) + "' at position " + pos);
            }
            if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
                try {
                    return Double.parseDouble(token);
                } catch (NumberFormatException e) {
                    throw new JsonException("Invalid number '" + token + "'");
                }
            }
            try {
                return Long.parseLong(token);
            } catch (NumberFormatException e) {
                throw new JsonException("Invalid number '" + token + "'");
            }
        }

        private static boolean isNumberChar(char c) {
            return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E';
        }

        private void expect(String literal) {
            if (!text.startsWith(literal, pos)) {
                throw new JsonException("Expected '" + literal + "' at position " + pos);
            }
            pos += literal.length();
        }

        private void expect(char c) {
            if (pos >= text.length() || text.charAt(pos) != c) {
                throw new JsonException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }

        private char peek() {
            if (pos >= text.length()) {
                throw new JsonException("Unexpected end of input");
            }
            return text.charAt(pos);
        }

        private void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    return;
                }
                pos++;
            }
        }
    }
}
