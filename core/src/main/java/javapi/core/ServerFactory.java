package javapi.core;

import javapi.routing.Router;

public interface ServerFactory {
    Server create(Router router, ServerSettings settings);

    default Server create(Router router, int port) {
        return create(router, ServerSettings.create().withPort(port));
    }
}
