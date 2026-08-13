package javapi.server;

import javapi.core.Server;
import javapi.core.ServerFactory;
import javapi.core.ServerSettings;
import javapi.routing.Router;

public final class NettyServerFactory implements ServerFactory {

    @Override
    public Server create(Router router, ServerSettings settings) {
        return new NettyServer(router, settings);
    }
}
