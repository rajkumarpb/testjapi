package javapi.validation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javapi.json.Json;
import javapi.json.JsonException;
import javapi.params.FieldError;
import javapi.params.RequestValidationError;
import javapi.schema.Constraints;
import javapi.schema.Schema;
import javapi.schema.Schemas;

public final class SchemaValidator {

    private SchemaValidator() {
    }

    public static Object validate(Type type, String json) {
        return validate(type, json, List.of("body"));
    }

    public static Object validate(Type type, String json, List<Object> rootLoc) {
        Object raw = Json.parse(json, Object.class);
        return validate(type, raw, rootLoc);
    }

    public static Object validate(Type type, Object rawValue, List<Object> rootLoc) {
        List<FieldError> errors = new ArrayList<>();
        Schema schema = Schemas.schemaOf(type);
        Object value = validateValue(schema, rawValue, rootLoc, errors, Constraints.NONE);
        if (!errors.isEmpty()) {
            throw new RequestValidationError(errors);
        }
        return value;
    }

    private static Object validateValue(Schema schema, Object raw, List<Object> loc,
            List<FieldError> errors, Constraints constraints) {
        switch (schema.kind()) {
            case OPTIONAL -> {
                if (raw == null) {
                    return Optional.empty();
                }
                return Optional.ofNullable(validateValue(schema.inner(), raw, loc, errors, constraints));
            }
            case STRING -> {
                if (!(raw instanceof String s)) {
                    error(loc, "string_type", "Input should be a valid string", errors);
                    return null;
                }
                checkConstraints(s, constraints, loc, errors);
                return s;
            }
            case INT -> {
                return integral(schema, raw, loc, errors, constraints,
                        l -> (int) l, "int_parsing", "Input should be a valid integer");
            }
            case LONG -> {
                return integral(schema, raw, loc, errors, constraints,
                        l -> l, "int_parsing", "Input should be a valid integer");
            }
            case SHORT -> {
                return integral(schema, raw, loc, errors, constraints,
                        l -> (short) l, "int_parsing", "Input should be a valid integer");
            }
            case BYTE -> {
                return integral(schema, raw, loc, errors, constraints,
                        l -> (byte) l, "int_parsing", "Input should be a valid integer");
            }
            case DOUBLE -> {
                return floating(schema, raw, loc, errors, constraints,
                        d -> d, "float_parsing", "Input should be a valid number");
            }
            case FLOAT -> {
                return floating(schema, raw, loc, errors, constraints,
                        d -> (float) d, "float_parsing", "Input should be a valid number");
            }
            case BOOLEAN -> {
                if (!(raw instanceof Boolean b)) {
                    error(loc, "bool_parsing", "Input should be a valid boolean", errors);
                    return null;
                }
                return b;
            }
            case CHAR -> {
                if (!(raw instanceof String s) || s.isEmpty()) {
                    error(loc, "string_type", "Input should be a valid string", errors);
                    return null;
                }
                return s.charAt(0);
            }
            case ENUM -> {
                return enumValue(schema, raw, loc, errors);
            }
            case UUID -> {
                if (!(raw instanceof String s)) {
                    error(loc, "uuid_parsing", "Input should be a valid UUID", errors);
                    return null;
                }
                try {
                    return UUID.fromString(s);
                } catch (IllegalArgumentException e) {
                    error(loc, "uuid_parsing", "Input should be a valid UUID", errors);
                    return null;
                }
            }
            case LOCAL_DATE -> {
                if (!(raw instanceof String s)) {
                    error(loc, "date_parsing", "Input should be a valid date", errors);
                    return null;
                }
                try {
                    return LocalDate.parse(s);
                } catch (DateTimeParseException e) {
                    error(loc, "date_parsing", "Input should be a valid date", errors);
                    return null;
                }
            }
            case LOCAL_DATE_TIME -> {
                if (!(raw instanceof String s)) {
                    error(loc, "datetime_parsing", "Input should be a valid datetime", errors);
                    return null;
                }
                try {
                    return LocalDateTime.parse(s);
                } catch (DateTimeParseException e) {
                    error(loc, "datetime_parsing", "Input should be a valid datetime", errors);
                    return null;
                }
            }
            case LIST -> {
                if (!(raw instanceof List<?> list)) {
                    error(loc, "list_type", "Input should be a valid array", errors);
                    return null;
                }
                List<Object> out = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    out.add(validateValue(schema.inner(), list.get(i), append(loc, i), errors, Constraints.NONE));
                }
                return out;
            }
            case SET -> {
                if (!(raw instanceof List<?> list)) {
                    error(loc, "list_type", "Input should be a valid array", errors);
                    return null;
                }
                List<Object> out = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    out.add(validateValue(schema.inner(), list.get(i), append(loc, i), errors, Constraints.NONE));
                }
                return Set.copyOf(out);
            }
            case MAP -> {
                if (!(raw instanceof Map<?, ?> map)) {
                    error(loc, "dict_type", "Input should be a valid dictionary", errors);
                    return null;
                }
                Map<String, Object> out = new LinkedHashMap<>(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    out.put(String.valueOf(entry.getKey()),
                            validateValue(schema.value(), entry.getValue(), append(loc, String.valueOf(entry.getKey())),
                                    errors, Constraints.NONE));
                }
                return out;
            }
            case RECORD -> {
                return recordValue(schema, raw, loc, errors);
            }
            case OBJECT -> {
                return raw;
            }
            default -> throw new IllegalStateException("Unexpected schema kind " + schema.kind());
        }
    }

    private interface LongConverter {
        Object convert(long value);
    }

    private interface DoubleConverter {
        Object convert(double value);
    }

    private static Object integral(Schema schema, Object raw, List<Object> loc, List<FieldError> errors,
            Constraints constraints, LongConverter converter, String type, String msg) {
        long value;
        if (raw instanceof Integer i) {
            value = i.longValue();
        } else if (raw instanceof Long l) {
            value = l;
        } else if (raw instanceof Short s) {
            value = s.longValue();
        } else if (raw instanceof Byte b) {
            value = b.longValue();
        } else {
            error(loc, type, msg, errors);
            return null;
        }
        checkConstraints(value, constraints, loc, errors);
        return converter.convert(value);
    }

    private static Object floating(Schema schema, Object raw, List<Object> loc, List<FieldError> errors,
            Constraints constraints, DoubleConverter converter, String type, String msg) {
        if (!(raw instanceof Number number)) {
            error(loc, type, msg, errors);
            return null;
        }
        double value = number.doubleValue();
        checkConstraints(value, constraints, loc, errors);
        return converter.convert(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Schema schema, Object raw, List<Object> loc, List<FieldError> errors) {
        if (!(raw instanceof String s)) {
            error(loc, "enum", "Input should be a valid " + schema.raw().getSimpleName(), errors);
            return null;
        }
        for (Enum constant : (Enum[]) schema.raw().getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(s)) {
                return constant;
            }
        }
        error(loc, "enum", "Input should be a valid " + schema.raw().getSimpleName(), errors);
        return null;
    }

    private static Object recordValue(Schema schema, Object raw, List<Object> loc, List<FieldError> errors) {
        if (!(raw instanceof Map<?, ?> map)) {
            error(loc, "dict_type", "Input should be a valid dictionary", errors);
            return null;
        }
        List<Schema.Field> fields = schema.fields();
        Class<?>[] types = new Class<?>[fields.size()];
        Object[] args = new Object[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            Schema.Field field = fields.get(i);
            types[i] = field.rawClass();
            List<Object> fieldLoc = append(loc, field.name());
            boolean present = map.containsKey(field.name());
            if (!present) {
                if (field.schema().isOptional()) {
                    args[i] = Optional.empty();
                } else if (field.constraints().optional()) {
                    args[i] = null;
                } else {
                    error(fieldLoc, "missing", "Field required", errors);
                }
            } else {
                Object fieldValue = map.get(field.name());
                if (fieldValue == null && !field.schema().isOptional()) {
                    if (field.constraints().optional()) {
                        args[i] = null;
                    } else {
                        error(fieldLoc, "not_nullable", "Input should not be null", errors);
                    }
                } else {
                    args[i] = validateValue(field.schema(), fieldValue, fieldLoc, errors, field.constraints());
                }
            }
        }
        if (errors.isEmpty()) {
            return construct(schema.raw(), types, args);
        }
        return null;
    }

    private static Object construct(Class<?> raw, Class<?>[] types, Object[] args) {
        try {
            Constructor<?> constructor = raw.getDeclaredConstructor(types);
            constructor.trySetAccessible();
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new JsonException("Failed to construct " + raw.getName(), e);
        }
    }

    private static void checkConstraints(Object value, Constraints constraints, List<Object> loc,
            List<FieldError> errors) {
        ConstraintError error = ConstraintValidator.check(value, constraints);
        if (error != null) {
            error(loc, error.type(), error.msg(), errors);
        }
    }

    private static List<Object> append(List<Object> loc, Object segment) {
        List<Object> next = new ArrayList<>(loc.size() + 1);
        next.addAll(loc);
        next.add(segment);
        return next;
    }

    private static void error(List<Object> loc, String type, String msg, List<FieldError> errors) {
        errors.add(new FieldError(loc, msg, type));
    }
}
