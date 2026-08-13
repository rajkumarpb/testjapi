package javapi.params;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javapi.annotations.Body;
import javapi.annotations.Cookie;
import javapi.annotations.Depends;
import javapi.annotations.File;
import javapi.annotations.Form;
import javapi.annotations.Header;
import javapi.annotations.Optional;
import javapi.annotations.Path;
import javapi.annotations.Query;
import javapi.annotations.Value;
import javapi.config.Config;
import javapi.json.JsonException;
import javapi.request.Request;
import javapi.schema.Constraints;
import javapi.validation.ConstraintError;
import javapi.validation.ConstraintValidator;
import javapi.validation.SchemaValidator;

public final class ParamBinder {

    private final List<ParamBinding> bindings;

    public ParamBinder(Method method) {
        this.bindings = analyze(method);
    }

    public Object[] bind(Request request) {
        Object[] args = new Object[bindings.size()];
        List<FieldError> errors = new ArrayList<>();
        for (int i = 0; i < bindings.size(); i++) {
            try {
                args[i] = bindOne(request, bindings.get(i));
            } catch (BindingError e) {
                errors.add(e.error());
            } catch (RequestValidationError e) {
                errors.addAll(e.errors());
            }
        }
        if (!errors.isEmpty()) {
            throw new RequestValidationError(errors);
        }
        return args;
    }

    public List<Integer> dependsPositions() {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < bindings.size(); i++) {
            if (bindings.get(i).source() == BindingSource.DEPENDS) {
                positions.add(i);
            }
        }
        return positions;
    }

    public Class<?> dependsType(int index) {
        return (Class<?>) bindings.get(index).type();
    }

    public List<ParamInfo> paramInfo() {
        List<ParamInfo> info = new ArrayList<>(bindings.size());
        for (ParamBinding binding : bindings) {
            info.add(new ParamInfo(
                    binding.name(), binding.source(), binding.type(), binding.optional(), binding.constraints()));
        }
        return info;
    }

    private Object bindOne(Request request, ParamBinding binding) {
        switch (binding.source()) {
            case PATH -> {
                return coerceOrNull(request.pathParam(binding.name()), binding);
            }
            case QUERY -> {
                return coerceOrNull(request.queryParam(binding.name()), binding);
            }
            case HEADER -> {
                return coerceOrNull(request.header(binding.name()), binding);
            }
            case COOKIE -> {
                return coerceOrNull(request.cookie(binding.name()), binding);
            }
            case FORM -> {
                return coerceOrNull(request.form(binding.name()), binding);
            }
            case FILE -> {
                return bindFile(request.file(binding.name()), binding);
            }
            case VALUE -> {
                return coerceOrNull(Config.load().get(binding.name()), binding);
            }
            case BODY -> {
                return bindBody(request.body(), binding);
            }
            case DEPENDS -> {
                return null;
            }
            default -> throw new IllegalStateException("Unknown binding source " + binding.source());
        }
    }

    private Object bindFile(UploadedFile file, ParamBinding binding) {
        if (file == null) {
            if (binding.optional() || isOptionalType(binding.type())) {
                return absentDefault(binding.type());
            }
            throw new BindingError(new FieldError(
                    List.of(sourceName(binding.source()), binding.name()),
                    "Field required", "missing"));
        }
        if (isOptionalType(binding.type())) {
            return java.util.Optional.of(file);
        }
        return file;
    }

    private Object coerceOrNull(String raw, ParamBinding binding) {
        if (raw == null) {
            if (binding.optional() || isOptionalType(binding.type())) {
                return absentDefault(binding.type());
            }
            throw new BindingError(new FieldError(
                    List.of(sourceName(binding.source()), binding.name()),
                    "Field required", "missing"));
        }
        try {
            Object coerced = ScalarCoercer.coerce(raw, binding.type());
            checkConstraints(coerced, binding);
            return coerced;
        } catch (CoercionError e) {
            throw new BindingError(new FieldError(
                    List.of(sourceName(binding.source()), binding.name()),
                    e.getMessage(), e.type()));
        }
    }

    private void checkConstraints(Object value, ParamBinding binding) {
        if (binding.constraints().none()) {
            return;
        }
        ConstraintError error = ConstraintValidator.check(value, binding.constraints());
        if (error != null) {
            throw new BindingError(new FieldError(
                    List.of(sourceName(binding.source()), binding.name()),
                    error.msg(), error.type()));
        }
    }

    private Object bindBody(String raw, ParamBinding binding) {
        Type type = binding.type();
        boolean optional = binding.optional() || isOptionalType(type);
        if (raw == null || raw.isBlank()) {
            if (optional) {
                return absentDefault(type);
            }
            throw new BindingError(new FieldError(
                    List.of("body"), "Field required", "missing"));
        }
        try {
            return SchemaValidator.validate(type, raw);
        } catch (JsonException e) {
            throw new BindingError(new FieldError(
                    List.of("body"), "Invalid JSON body: " + e.getMessage(), "json_invalid"));
        }
    }

    private static boolean isOptionalType(Type type) {
        return type instanceof ParameterizedType p && p.getRawType() == java.util.Optional.class;
    }

    private static Object absentDefault(Type type) {
        if (type instanceof ParameterizedType p && p.getRawType() == java.util.Optional.class) {
            return java.util.Optional.empty();
        }
        Class<?> raw = (Class<?>) type;
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

    private static List<ParamBinding> analyze(Method method) {
        Parameter[] parameters = method.getParameters();
        List<ParamBinding> bindings = new ArrayList<>(parameters.length);
        boolean sawBody = false;
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter.isAnnotationPresent(Depends.class)) {
                bindings.add(new ParamBinding("depends", BindingSource.DEPENDS,
                        parameter.getParameterizedType(), false, Constraints.NONE));
                continue;
            }
            Annotation annotation = null;
            int sources = 0;
            for (Annotation candidate : parameter.getAnnotations()) {
                if (candidate instanceof Path
                        || candidate instanceof Query
                        || candidate instanceof Header
                        || candidate instanceof Cookie
                        || candidate instanceof Body
                        || candidate instanceof Form
                        || candidate instanceof File
                        || candidate instanceof Value) {
                    annotation = candidate;
                    sources++;
                }
            }
            if (sources > 1) {
                throw new IllegalStateException(
                        "Parameter " + i + " of " + method.getDeclaringClass().getName() + "."
                                + method.getName() + " has multiple binding annotations");
            }
            String name;
            BindingSource source;
            if (annotation == null) {
                if (i == 0 && parameters.length == 1 && !primitiveLike(parameter.getType())) {
                    name = "body";
                    source = BindingSource.BODY;
                } else {
                    throw new IllegalStateException(
                            "Parameter " + i + " of " + method.getDeclaringClass().getName() + "."
                                    + method.getName() + " must declare a binding annotation "
                                    + "(@Path, @Query, @Header, @Cookie, @Body, @Form, @File or @Value)");
                }
            } else if (annotation instanceof Path p) {
                name = named(parameter, p.value(), i, method, "path");
                source = BindingSource.PATH;
            } else if (annotation instanceof Query q) {
                name = named(parameter, q.value(), i, method, "query");
                source = BindingSource.QUERY;
            } else if (annotation instanceof Header h) {
                name = named(parameter, h.value(), i, method, "header");
                source = BindingSource.HEADER;
            } else if (annotation instanceof Cookie c) {
                name = named(parameter, c.value(), i, method, "cookie");
                source = BindingSource.COOKIE;
            } else if (annotation instanceof Form f) {
                name = named(parameter, f.value(), i, method, "form");
                source = BindingSource.FORM;
            } else if (annotation instanceof File f) {
                name = named(parameter, f.value(), i, method, "file");
                checkFileType(method, parameter.getParameterizedType());
                source = BindingSource.FILE;
            } else if (annotation instanceof Value v) {
                name = v.value();
                if (name == null || name.isBlank()) {
                    throw new IllegalStateException(
                            "Parameter " + i + " of " + method.getDeclaringClass().getName() + "."
                                    + method.getName() + " has an empty @Value key");
                }
                source = BindingSource.VALUE;
            } else {
                name = "body";
                source = BindingSource.BODY;
            }
            if (source == BindingSource.BODY) {
                if (sawBody) {
                    throw new IllegalStateException(
                            "Method " + method.getDeclaringClass().getName() + "." + method.getName()
                                    + " has more than one body parameter");
                }
                sawBody = true;
            }
            if (name.isBlank()) {
                throw new IllegalStateException(
                        "Parameter " + i + " of " + method.getDeclaringClass().getName() + "."
                                + method.getName() + " has an empty name");
            }
            boolean optional = parameter.isAnnotationPresent(Optional.class);
            Constraints constraints = Constraints.of(parameter);
            bindings.add(new ParamBinding(name, source, parameter.getParameterizedType(), optional, constraints));
        }
        return List.copyOf(bindings);
    }

    private static void checkFileType(Method method, Type type) {
        if (type == UploadedFile.class) {
            return;
        }
        if (type instanceof ParameterizedType p && p.getRawType() == java.util.Optional.class
                && p.getActualTypeArguments().length == 1
                && p.getActualTypeArguments()[0] == UploadedFile.class) {
            return;
        }
        throw new IllegalStateException(
                "@File parameter in " + method.getDeclaringClass().getName() + "." + method.getName()
                        + " must be of type UploadedFile or Optional<UploadedFile>");
    }

    private static String sourceName(BindingSource source) {
        return switch (source) {
            case PATH -> "path";
            case QUERY -> "query";
            case HEADER -> "header";
            case COOKIE -> "cookie";
            case BODY -> "body";
            case FORM -> "form";
            case FILE -> "file";
            case DEPENDS -> "depends";
            case VALUE -> "config";
        };
    }

    private static boolean primitiveLike(Class<?> type) {
        return type.isPrimitive() || type == String.class || type.isEnum() || type == Integer.class
                || type == Long.class || type == Double.class || type == Float.class || type == Boolean.class
                || type == Short.class || type == Byte.class || type == Character.class;
    }

    private static String named(Parameter parameter, String annotated, int index, Method method, String kind) {
        if (annotated != null && !annotated.isBlank()) {
            return annotated;
        }
        String name = parameter.getName();
        if (name == null || (name.startsWith("arg") && name.length() > 3 && name.substring(3).chars()
                .allMatch(Character::isDigit))) {
            throw new IllegalStateException(
                    "Cannot resolve the name of parameter " + index + " of "
                            + method.getDeclaringClass().getName() + "." + method.getName()
                            + " (compile with -parameters or set @" + kind + "(\"name\"))");
        }
        return name;
    }
}
