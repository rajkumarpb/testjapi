package javapi.routing;

import java.util.Map;

public record RouteMatch(Route route, Map<String, String> pathParams) {
}
