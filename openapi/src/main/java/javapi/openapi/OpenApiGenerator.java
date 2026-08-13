package javapi.openapi;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javapi.annotations.HttpMethod;
import javapi.params.BindingSource;
import javapi.params.ParamInfo;
import javapi.routing.EndpointMeta;
import javapi.routing.Route;
import javapi.routing.Router;
import javapi.schema.Constraints;
import javapi.schema.Schema;
import javapi.schema.Schemas;

public final class OpenApiGenerator {

    private static final Set<String> INTERNAL_PATHS = Set.of("/openapi.json", "/docs", "/redoc");

    private OpenApiGenerator() {
    }

    public static Map<String, Object> generate(Router router) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("openapi", "3.1.0");
        document.put("info", Map.of("title", "javapi", "version", "0.1.0"));
        Map<String, Object> paths = new LinkedHashMap<>();
        for (Route route : router.routes()) {
            if (INTERNAL_PATHS.contains(route.path())) {
                continue;
            }
            String openApiPath = convert(route.path());
            @SuppressWarnings("unchecked")
            Map<String, Object> operations = (Map<String, Object>)
                    paths.computeIfAbsent(openApiPath, key -> new LinkedHashMap<>());
            for (HttpMethod method : route.methods()) {
                operations.put(method.name().toLowerCase(), operation(route, method));
            }
        }
        document.put("paths", paths);
        return document;
    }

    private static Map<String, Object> operation(Route route, HttpMethod method) {
        Map<String, Object> operation = new LinkedHashMap<>();
        EndpointMeta meta = route.meta();
        String id = meta == null
                ? method.name().toLowerCase()
                : meta.declaringClass().getSimpleName() + "_" + meta.methodName();
        operation.put("operationId", id);
        operation.put("summary", id);

        List<Object> parameters = new ArrayList<>();
        ParamInfo bodyParam = null;
        List<ParamInfo> formParams = new ArrayList<>();
        List<ParamInfo> fileParams = new ArrayList<>();
        if (meta != null) {
            for (ParamInfo param : meta.params()) {
                if (param.source() == BindingSource.DEPENDS || param.source() == BindingSource.VALUE) {
                    continue;
                }
                if (param.source() == BindingSource.BODY) {
                    bodyParam = param;
                } else if (param.source() == BindingSource.FORM) {
                    formParams.add(param);
                } else if (param.source() == BindingSource.FILE) {
                    fileParams.add(param);
                } else {
                    parameters.add(parameter(param));
                }
            }
        }
        if (!parameters.isEmpty()) {
            operation.put("parameters", parameters);
        }
        if (!fileParams.isEmpty()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            for (ParamInfo param : fileParams) {
                properties.put(param.name(), Map.of("type", "string", "format", "binary"));
            }
            operation.put("requestBody", Map.of(
                    "required", true,
                    "content", Map.of("multipart/form-data",
                            Map.of("schema", Map.of("type", "object", "properties", properties)))));
        } else if (!formParams.isEmpty()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            for (ParamInfo param : formParams) {
                properties.put(param.name(), toJsonSchema(Schemas.schemaOf(param.type()), param.constraints()));
                if (!param.optional()) {
                    required.add(param.name());
                }
            }
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", properties);
            if (!required.isEmpty()) {
                schema.put("required", required);
            }
            operation.put("requestBody", Map.of(
                    "required", true,
                    "content", Map.of("application/x-www-form-urlencoded",
                            Map.of("schema", schema))));
        } else if (bodyParam != null) {
            Schema schema = Schemas.schemaOf(bodyParam.type());
            operation.put("requestBody", Map.of(
                    "required", !(bodyParam.optional() || schema.isOptional()),
                    "content", Map.of("application/json",
                            Map.of("schema", toJsonSchema(schema, bodyParam.constraints())))));
        }
        operation.put("responses", Map.of("200", response(meta == null ? void.class : meta.returnType())));
        return operation;
    }

    private static Map<String, Object> parameter(ParamInfo param) {
        Map<String, Object> parameter = new LinkedHashMap<>();
        String in = switch (param.source()) {
            case PATH -> "path";
            case QUERY -> "query";
            case HEADER -> "header";
            case COOKIE -> "cookie";
            case BODY, DEPENDS, VALUE, FORM, FILE -> "query";
        };
        parameter.put("in", in);
        parameter.put("name", param.name());
        parameter.put("required", "path".equals(in) || !isOptional(param));
        parameter.put("schema", toJsonSchema(Schemas.schemaOf(param.type()), param.constraints()));
        return parameter;
    }

    private static boolean isOptional(ParamInfo param) {
        if (param.optional()) {
            return true;
        }
        Type type = param.type();
        return type instanceof ParameterizedType p && p.getRawType() == Optional.class;
    }

    private static Map<String, Object> response(Type returnType) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", "OK");
        if (returnType != void.class) {
            response.put("content", Map.of("application/json",
                    Map.of("schema", toJsonSchema(Schemas.schemaOf(returnType), Constraints.NONE))));
        }
        return response;
    }

    private static Map<String, Object> toJsonSchema(Schema schema, Constraints constraints) {
        Map<String, Object> json = new LinkedHashMap<>();
        switch (schema.kind()) {
            case STRING, CHAR -> json.put("type", "string");
            case INT, SHORT, BYTE -> json.put("type", "integer");
            case LONG -> {
                json.put("type", "integer");
                json.put("format", "int64");
            }
            case DOUBLE -> {
                json.put("type", "number");
                json.put("format", "double");
            }
            case FLOAT -> {
                json.put("type", "number");
                json.put("format", "float");
            }
            case BOOLEAN -> json.put("type", "boolean");
            case ENUM -> {
                json.put("type", "string");
                List<Object> values = new ArrayList<>();
                for (Object constant : schema.raw().getEnumConstants()) {
                    values.add(constant.toString());
                }
                json.put("enum", values);
            }
            case UUID -> {
                json.put("type", "string");
                json.put("format", "uuid");
            }
            case LOCAL_DATE -> {
                json.put("type", "string");
                json.put("format", "date");
            }
            case LOCAL_DATE_TIME -> {
                json.put("type", "string");
                json.put("format", "date-time");
            }
            case OPTIONAL -> {
                return toJsonSchema(schema.inner(), constraints);
            }
            case LIST, SET -> {
                json.put("type", "array");
                json.put("items", toJsonSchema(schema.inner(), Constraints.NONE));
            }
            case MAP -> {
                json.put("type", "object");
                json.put("additionalProperties", toJsonSchema(schema.value(), Constraints.NONE));
            }
            case RECORD -> {
                json.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                List<String> required = new ArrayList<>();
                for (Schema.Field field : schema.fields()) {
                    properties.put(field.name(), toJsonSchema(field.schema(), field.constraints()));
                    if (!field.schema().isOptional() && !field.constraints().optional()) {
                        required.add(field.name());
                    }
                }
                json.put("properties", properties);
                if (!required.isEmpty()) {
                    json.put("required", required);
                }
            }
            case OBJECT -> {
            }
        }
        applyConstraints(json, constraints);
        return json;
    }

    private static void applyConstraints(Map<String, Object> json, Constraints constraints) {
        if (constraints == null || constraints.none()) {
            return;
        }
        if (constraints.minLength() != null) {
            json.put("minLength", constraints.minLength());
        }
        if (constraints.maxLength() != null) {
            json.put("maxLength", constraints.maxLength());
        }
        if (constraints.min() != null) {
            json.put("minimum", constraints.min());
        }
        if (constraints.max() != null) {
            json.put("maximum", constraints.max());
        }
        if (constraints.pattern() != null) {
            json.put("pattern", constraints.pattern().pattern());
        }
        if (constraints.email()) {
            json.put("format", "email");
        }
    }

    private static String convert(String path) {
        return path.replaceAll(":([A-Za-z0-9_]+)", "{$1}");
    }
}
