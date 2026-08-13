package javapi.routing;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import javapi.params.ParamInfo;

public record EndpointMeta(
        Class<?> declaringClass,
        String methodName,
        List<ParamInfo> params,
        Type returnType) {

    public EndpointMeta {
        params = List.copyOf(params);
    }

    public static EndpointMeta of(Method method, List<ParamInfo> params) {
        return new EndpointMeta(method.getDeclaringClass(), method.getName(), params, method.getGenericReturnType());
    }
}
